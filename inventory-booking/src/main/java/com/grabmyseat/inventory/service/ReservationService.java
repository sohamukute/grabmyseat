package com.grabmyseat.inventory.service;

import com.grabmyseat.inventory.dto.ReserveSeatsRequest;
import com.grabmyseat.inventory.dto.ReservationResponse;
import com.grabmyseat.inventory.model.Event;
import com.grabmyseat.inventory.model.Reservation;
import com.grabmyseat.inventory.model.ReservationStatus;
import com.grabmyseat.inventory.model.SaleType;
import com.grabmyseat.inventory.model.Seat;
import com.grabmyseat.inventory.model.SeatStatus;
import com.grabmyseat.inventory.repository.EventRepository;
import com.grabmyseat.inventory.repository.ReservationRepository;
import com.grabmyseat.inventory.repository.SeatRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ReservationService {

    private static final int MAX_SEATS_PER_BOOKING = 4;
    private static final int MAX_ACTIVE_HELD_OR_CONFIRMED = 4;
    private static final Duration HOLD_TTL = Duration.ofMinutes(5);

    private final ReservationRepository reservations;
    private final SeatRepository seats;
    private final EventRepository events;
    private final CapacityService capacityService;
    private final RedissonClient redisson;
    private final QueuePermitService queuePermitService;
    private final java.util.Optional<com.grabmyseat.inventory.client.WaitingRoomClient> waitingRoomClient;
    private final Counter reservationsCreatedCounter;
    private final Counter waitlistOffersSentCounter;

    public ReservationService(ReservationRepository reservations,
                              SeatRepository seats,
                              EventRepository events,
                              CapacityService capacityService,
                              RedissonClient redisson,
                              QueuePermitService queuePermitService,
                              java.util.Optional<com.grabmyseat.inventory.client.WaitingRoomClient> waitingRoomClient,
                              MeterRegistry meterRegistry) {
        this.reservations = reservations;
        this.seats = seats;
        this.events = events;
        this.capacityService = capacityService;
        this.redisson = redisson;
        this.queuePermitService = queuePermitService;
        this.waitingRoomClient = waitingRoomClient;
        this.reservationsCreatedCounter = Counter.builder("grabmyseat.reservations.created")
                .description("Total number of reservations created")
                .register(meterRegistry);
        this.waitlistOffersSentCounter = Counter.builder("grabmyseat.waitlist.offers.sent")
                .description("Total number of waitlist offers sent")
                .register(meterRegistry);
    }

    /**
     * Auto-allocates {@code quantity} available seats in the zone and reserves them.
     * Standard-sale events skip the queue permit check entirely (there is no queue to
     * be admitted from); flash sales still require an unexpired permit issued by the
     * waiting room.
     */
    @Transactional
    public ReservationResponse reserveByQuantity(Long userId, String ownerName, String queuePermit,
                                                  Long eventId, Long zoneId, int quantity, List<String> attendeeNames) {
        if (quantity < 1 || quantity > MAX_SEATS_PER_BOOKING) {
            throw new IllegalArgumentException("choose between 1 and " + MAX_SEATS_PER_BOOKING + " tickets");
        }
        List<Long> seatIds = seats.findByZoneIdOrderByRowLabelAscNumberAsc(zoneId).stream()
                .filter(seat -> seat.getStatus() == SeatStatus.AVAILABLE)
                .limit(quantity)
                .map(Seat::getId)
                .toList();
        if (seatIds.size() < quantity) {
            throw new IllegalStateException("not enough seats available in this zone");
        }
        return reserve(userId, ownerName, queuePermit, new ReserveSeatsRequest(eventId, zoneId, seatIds, attendeeNames));
    }

    @Transactional
    public ReservationResponse reserve(Long userId, String ownerName, String queuePermit, ReserveSeatsRequest request) {
        Event event = events.findById(request.eventId())
                .orElseThrow(() -> new IllegalArgumentException("event not found"));
        if (event.effectiveSaleType(Instant.now()) == SaleType.FLASH) {
            queuePermitService.validate(queuePermit, request.eventId(), userId);
        }

        List<Long> seatIds = request.seatIds().stream().distinct().sorted().toList();
        List<String> attendeeNames = normalizeAttendees(request.attendeeNames(), ownerName, seatIds.size());
        if (seatIds.size() > MAX_SEATS_PER_BOOKING) {
            throw new IllegalArgumentException("max " + MAX_SEATS_PER_BOOKING + " seats per booking");
        }


        List<RLock> locks = seatIds.stream()
                .map(id -> redisson.getLock("seat:" + id))
                .toList();

        boolean allLocked = false;
        boolean capacityDecremented = false;
        try {
            // Lock all seats in consistent order (already sorted) with a short wait
            for (RLock lock : locks) {
                if (!lock.tryLock(2, 10, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("could not acquire lock for seat");
                }
            }
            allLocked = true;

            // Verify seats belong to zone and are AVAILABLE
            List<Seat> seatEntities = seats.findAllById(seatIds);
            if (seatEntities.size() != seatIds.size()) {
                throw new IllegalArgumentException("one or more seats do not exist");
            }
            for (Seat seat : seatEntities) {
                if (!seat.getZone().getId().equals(request.zoneId())) {
                    throw new IllegalArgumentException("seat does not belong to zone");
                }
                if (seat.getStatus() != SeatStatus.AVAILABLE) {
                    throw new IllegalStateException("seat " + seat.getId() + " is not available");
                }
            }

            // Atomic capacity check + decrement in Redis
            if (!capacityService.reserve(request.zoneId(), seatIds.size())) {
                throw new IllegalStateException("not enough zone capacity");
            }
            capacityDecremented = true;

            // Optimistic-lock seat status update
            int updated = 0;
            for (Seat seat : seatEntities) {
                int rows = seats.updateStatus(seat.getId(), SeatStatus.AVAILABLE, SeatStatus.HELD);
                if (rows == 0) {
                    throw new IllegalStateException("seat " + seat.getId() + " was concurrently modified");
                }
                updated += rows;
            }

            RLock activeTicketsLock = redisson.getLock("active-tickets:" + userId + ":" + request.eventId());
            boolean activeTicketsLockReleasedAfterTransaction = false;
            String token;
            Reservation reservation;
            try {
                if (!activeTicketsLock.tryLock(2, 10, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("could not acquire lock for active tickets");
                }
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                if (activeTicketsLock.isHeldByCurrentThread()) {
                                    activeTicketsLock.unlock();
                                }
                            }
                        });
                activeTicketsLockReleasedAfterTransaction = true;

                long activeCount = reservations.sumQuantityByUserIdAndEventIdAndStatusIn(
                        userId, request.eventId(),
                        List.of(ReservationStatus.HELD, ReservationStatus.CONFIRMED));
                if (activeCount + seatIds.size() > MAX_ACTIVE_HELD_OR_CONFIRMED) {
                    throw new IllegalArgumentException("max " + MAX_ACTIVE_HELD_OR_CONFIRMED + " active tickets per user per event");
                }

                token = UUID.randomUUID().toString();
                Instant expiresAt = Instant.now().plus(HOLD_TTL);
                BigDecimal zonePrice = seatEntities.get(0).getZone().getPrice();
                BigDecimal totalPrice = zonePrice.multiply(BigDecimal.valueOf(seatIds.size()));
                reservation = new Reservation(
                        userId, request.eventId(), request.zoneId(), seatIds, attendeeNames, expiresAt, token, totalPrice);
                reservation = reservations.save(reservation);
                reservationsCreatedCounter.increment();
            } finally {
                if (!activeTicketsLockReleasedAfterTransaction && activeTicketsLock.isHeldByCurrentThread()) {
                    activeTicketsLock.unlock();
                }
            }

            capacityService.setHold(token, request.eventId(), HOLD_TTL);

            return ReservationResponse.from(reservation);
        } catch (InterruptedException ex) {
            if (capacityDecremented) {
                capacityService.release(request.zoneId(), seatIds.size());
            }
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while acquiring locks");
        } catch (RuntimeException ex) {
            if (capacityDecremented) {
                capacityService.release(request.zoneId(), seatIds.size());
            }
            throw ex;
        } finally {
            if (allLocked) {
                for (int i = locks.size() - 1; i >= 0; i--) {
                    RLock lock = locks.get(i);
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
        }
    }

    @Transactional
    public ReservationResponse reserve(Long userId, String queuePermit, ReserveSeatsRequest request) {
        return reserve(userId, "Guest", queuePermit, request);
    }

    @Transactional(readOnly = true)
    public Optional<ReservationResponse> findByToken(String token) {
        return reservations.findByToken(token).map(ReservationResponse::from);
    }

    @Transactional
    public List<Reservation> expireHeldReservations() {
        List<Reservation> expired = reservations.findByStatusAndExpiresAtBefore(
                ReservationStatus.HELD, Instant.now());
        List<Reservation> released = new ArrayList<>();
        for (Reservation r : expired) {
            if (releaseReservation(r, ReservationStatus.EXPIRED)) {
                released.add(r);
            }
        }
        return released;
    }

    @Transactional
    public void cancel(Long userId, String token) {
        Reservation r = reservations.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("reservation not found"));
        if (!r.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("not your reservation");
        }
        if (r.getStatus() != ReservationStatus.HELD && r.getStatus() != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("reservation cannot be cancelled");
        }
        if (releaseReservation(r, ReservationStatus.CANCELLED)) {
            waitingRoomClient.ifPresent(client -> {
                client.notifyRelease(r.getEventId(), r.getZoneId(), r.getSeatIds().size());
                waitlistOffersSentCounter.increment();
            });
        }
    }

    @Transactional
    public void confirm(Long userId, String token) {
        Reservation r = reservations.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("reservation not found"));
        if (!r.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("not your reservation");
        }
        if (r.getStatus() != ReservationStatus.HELD) {
            throw new IllegalStateException("reservation is not held");
        }
        if (r.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("reservation expired");
        }
        int rows = reservations.updateStatus(token, ReservationStatus.HELD, ReservationStatus.CONFIRMED);
        if (rows == 0) {
            throw new IllegalStateException("reservation was concurrently modified");
        }
        for (Long seatId : r.getSeatIds()) {
            int seatRows = seats.updateStatus(seatId, SeatStatus.HELD, SeatStatus.SOLD);
            if (seatRows == 0) {
                throw new IllegalStateException("seat was concurrently modified");
            }
        }
        r.setStatus(ReservationStatus.CONFIRMED);
        r.setConfirmedAt(Instant.now());
        capacityService.removeHold(token);
    }

    private boolean releaseReservation(Reservation r, ReservationStatus newStatus) {
        int rows = reservations.updateStatus(r.getToken(), r.getStatus(), newStatus);
        if (rows == 0) {
            return false;
        }
        for (Long seatId : r.getSeatIds()) {
            seats.updateStatus(seatId, SeatStatus.HELD, SeatStatus.AVAILABLE);
            seats.updateStatus(seatId, SeatStatus.SOLD, SeatStatus.AVAILABLE);
        }
        capacityService.release(r.getZoneId(), r.getSeatIds().size());
        capacityService.removeHold(r.getToken());
        return true;
    }

    private List<String> normalizeAttendees(List<String> requested, String ownerName, int seatCount) {
        List<String> names = requested == null || requested.isEmpty()
                ? java.util.Collections.nCopies(seatCount, ownerName == null || ownerName.isBlank() ? "Guest" : ownerName.trim())
                : requested.stream().map(String::trim).toList();
        if (names.size() != seatCount || names.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("provide one attendee name for each seat");
        }
        if (new HashSet<>(names).size() != names.size()) {
            throw new IllegalArgumentException("each attendee name must be unique for this group ticket");
        }
        return names;
    }
}

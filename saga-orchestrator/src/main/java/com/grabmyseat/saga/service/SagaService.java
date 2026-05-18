package com.grabmyseat.saga.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grabmyseat.saga.client.ClientException;
import com.grabmyseat.saga.client.InventoryClient;
import com.grabmyseat.saga.client.TicketingClient;
import com.grabmyseat.saga.client.WaitingRoomClient;
import com.grabmyseat.saga.client.WalletClient;
import com.grabmyseat.saga.dto.ReservationDto;
import com.grabmyseat.saga.model.SagaInstance;
import com.grabmyseat.saga.model.SagaOutbox;
import com.grabmyseat.saga.model.SagaStatus;
import com.grabmyseat.saga.model.SagaStep;
import com.grabmyseat.saga.repository.SagaInstanceRepository;
import com.grabmyseat.saga.repository.SagaOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class SagaService {

    private static final Logger log = LoggerFactory.getLogger(SagaService.class);

    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaOutboxRepository sagaOutboxRepository;
    private final InventoryClient inventoryClient;
    private final WalletClient walletClient;
    private final Optional<WaitingRoomClient> waitingRoomClient;
    private final TicketingClient ticketingClient;
    private final ObjectMapper objectMapper;

    public SagaService(SagaInstanceRepository sagaInstanceRepository,
                       SagaOutboxRepository sagaOutboxRepository,
                       InventoryClient inventoryClient,
                       WalletClient walletClient,
                       Optional<WaitingRoomClient> waitingRoomClient,
                       TicketingClient ticketingClient,
                       ObjectMapper objectMapper) {
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.sagaOutboxRepository = sagaOutboxRepository;
        this.inventoryClient = inventoryClient;
        this.walletClient = walletClient;
        this.waitingRoomClient = waitingRoomClient;
        this.ticketingClient = ticketingClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(noRollbackFor = SagaException.class)
    public SagaInstance confirmBooking(Long userId, String reservationToken) {
        Optional<SagaInstance> existing = sagaInstanceRepository.findByReservationToken(reservationToken);
        if (existing.isPresent()) {
            return existing.get();
        }

        ReservationDto reservation = inventoryClient.getReservation(reservationToken);
        validateReservation(userId, reservation);

        BigDecimal totalAmount = computeTotalAmount(reservation);
        SagaInstance saga = new SagaInstance(
                reservationToken, userId, reservation.eventId(), reservation.zoneId(),
                reservation.seatIds(), totalAmount, SagaStatus.STARTED, reservation.expiresAt());
        saga = sagaInstanceRepository.save(saga);
        appendOutbox(saga, SagaStep.DEBIT_REQUESTED, Map.of("amount", totalAmount.toPlainString()));

        try {
            walletClient.debit(userId, totalAmount, reservationToken, reservationToken);
            appendOutbox(saga, SagaStep.DEBITED, Map.of("amount", totalAmount.toPlainString()));
            saga.setStatus(SagaStatus.DEBITED);
        } catch (ClientException ex) {
            if (ex.getStatus().value() == 409 && ex.getBody().contains("DUPLICATE_IDEMPOTENCY_KEY")) {
                appendOutbox(saga, SagaStep.DEBIT_DUPLICATE, Map.of("amount", totalAmount.toPlainString()));
                saga.setStatus(SagaStatus.DEBITED);
            } else {
                return handleDebitFailure(saga, ex);
            }
        } catch (Exception ex) {
            return handleDebitFailure(saga, ex);
        }

        appendOutbox(saga, SagaStep.CONFIRM_REQUESTED, Map.of());
        try {
            inventoryClient.confirmReservation(reservationToken, userId);
            appendOutbox(saga, SagaStep.CONFIRMED, Map.of());
            saga.setStatus(SagaStatus.CONFIRMED);
        } catch (ClientException ex) {
            appendOutbox(saga, SagaStep.CONFIRM_FAILED, Map.of("error", ex.getMessage()));
            return compensate(saga, userId);
        } catch (Exception ex) {
            appendOutbox(saga, SagaStep.CONFIRM_FAILED, Map.of("error", ex.getMessage()));
            return compensate(saga, userId);
        }

        try {
            ticketingClient.issueTicket(userId, reservationToken, saga.getEventId(), saga.getZoneId(),
                    saga.getSeatIds(), reservation.attendeeNames(), saga.getTotalAmount());
            appendOutbox(saga, SagaStep.TICKET_ISSUED, Map.of());
        } catch (Exception ex) {
            appendOutbox(saga, SagaStep.TICKET_ISSUE_FAILED, Map.of("error", ex.getMessage()));
        }

        return saga;
    }

    @Transactional(noRollbackFor = SagaException.class)
    public SagaInstance cancelBooking(Long userId, String reservationToken) {
        SagaInstance saga = sagaInstanceRepository.findByReservationToken(reservationToken)
                .orElseThrow(() -> new SagaException(SagaException.Code.RESERVATION_NOT_FOUND,
                        "saga not found for reservation " + reservationToken));
        if (!saga.getUserId().equals(userId)) {
            throw new SagaException(SagaException.Code.NOT_OWNER, "not your reservation");
        }
        if (saga.getStatus() != SagaStatus.CONFIRMED) {
            throw new SagaException(SagaException.Code.RESERVATION_NOT_HELD,
                    "booking cannot be cancelled in status " + saga.getStatus());
        }
        return compensate(saga, userId);
    }

    @Transactional(noRollbackFor = SagaException.class)
    public SagaInstance compensate(SagaInstance saga, Long userId) {
        saga.setStatus(SagaStatus.COMPENSATING);
        appendOutbox(saga, SagaStep.COMPENSATION_STARTED, Map.of());

        boolean refundFailed = false;
        try {
            walletClient.credit(userId, saga.getTotalAmount(), saga.getReservationToken() + "-refund", saga.getReservationToken());
            appendOutbox(saga, SagaStep.REFUNDED, Map.of("amount", saga.getTotalAmount().toPlainString()));
        } catch (ClientException ex) {
            appendOutbox(saga, SagaStep.REFUND_FAILED, Map.of("error", ex.getMessage()));
            refundFailed = true;
        } catch (Exception ex) {
            appendOutbox(saga, SagaStep.REFUND_FAILED, Map.of("error", ex.getMessage()));
            refundFailed = true;
        }

        boolean releaseFailed = false;
        try {
            inventoryClient.cancelReservation(saga.getReservationToken(), userId);
            appendOutbox(saga, SagaStep.RELEASED, Map.of());
            saga.setStatus(SagaStatus.COMPENSATED);
            waitingRoomClient.ifPresent(client -> {
                try {
                    client.notifyRelease(saga.getEventId(), saga.getZoneId(), saga.getSeatIds().size());
                } catch (Exception notifyEx) {
                    log.warn("failed to notify waiting room of release: {}", notifyEx.getMessage());
                }
            });
        } catch (ClientException ex) {
            appendOutbox(saga, SagaStep.RELEASE_FAILED, Map.of("error", ex.getMessage()));
            releaseFailed = true;
        } catch (Exception ex) {
            appendOutbox(saga, SagaStep.RELEASE_FAILED, Map.of("error", ex.getMessage()));
            releaseFailed = true;
        }

        if (releaseFailed) {
            throw new SagaException(SagaException.Code.COMPENSATION_ERROR, "seat release failed");
        }

        return saga;
    }

    @Transactional(readOnly = true)
    public Optional<SagaInstance> findByReservationToken(String reservationToken) {
        return sagaInstanceRepository.findByReservationToken(reservationToken);
    }

    private SagaInstance handleDebitFailure(SagaInstance saga, ClientException ex) {
        appendOutbox(saga, SagaStep.DEBIT_FAILED, Map.of("status", String.valueOf(ex.getStatus().value()), "body", ex.getBody()));
        saga.setStatus(SagaStatus.FAILED);
        if (ex.getStatus().value() == 409 && ex.getBody().contains("INSUFFICIENT_FUNDS")) {
            throw new SagaException(SagaException.Code.INSUFFICIENT_FUNDS, "insufficient funds");
        }
        throw new SagaException(SagaException.Code.PAYMENT_ERROR, "payment failed: " + ex.getBody());
    }

    private SagaInstance handleDebitFailure(SagaInstance saga, Exception ex) {
        appendOutbox(saga, SagaStep.DEBIT_FAILED, Map.of("error", ex.getMessage()));
        saga.setStatus(SagaStatus.FAILED);
        throw new SagaException(SagaException.Code.PAYMENT_ERROR, "payment failed: " + ex.getMessage());
    }

    private void validateReservation(Long userId, ReservationDto reservation) {
        if (reservation == null) {
            throw new SagaException(SagaException.Code.RESERVATION_NOT_FOUND, "reservation not found");
        }
        if (!"HELD".equalsIgnoreCase(reservation.status())) {
            throw new SagaException(SagaException.Code.RESERVATION_NOT_HELD,
                    "reservation is not held: " + reservation.status());
        }
        if (reservation.expiresAt().isBefore(Instant.now())) {
            throw new SagaException(SagaException.Code.RESERVATION_EXPIRED, "reservation expired");
        }
    }

    private BigDecimal computeTotalAmount(ReservationDto reservation) {
        // Price is snapshotted at reservation time in inventory-booking and
        // returned with the reservation. Never call getZonePrice() here: a
        // post-hold price change would silently charge a different amount than
        // what the customer saw at hold time.
        BigDecimal snapshot = reservation.totalPrice();
        if (snapshot == null) {
            throw new SagaException(SagaException.Code.CONFIRM_ERROR, "reservation price snapshot missing");
        }
        return snapshot;
    }

    private void appendOutbox(SagaInstance saga, SagaStep step, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            sagaOutboxRepository.save(new SagaOutbox(saga, step, json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize outbox payload", e);
        }
    }
}

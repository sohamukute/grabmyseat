package com.grabmyseat.inventory.service;

import com.grabmyseat.inventory.dto.ReserveSeatsRequest;
import com.grabmyseat.inventory.model.Event;
import com.grabmyseat.inventory.model.Seat;
import com.grabmyseat.inventory.model.Reservation;
import com.grabmyseat.inventory.model.ReservationStatus;
import com.grabmyseat.inventory.model.SeatStatus;
import com.grabmyseat.inventory.model.Zone;
import com.grabmyseat.inventory.repository.ReservationRepository;
import com.grabmyseat.inventory.repository.SeatRepository;
import jakarta.persistence.EntityManagerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:reservation-concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({ReservationService.class, CapacityService.class, ReservationServiceConcurrencyTest.MeterRegistryConfig.class})
class ReservationServiceConcurrencyTest {

    @TestConfiguration
    static class MeterRegistryConfig {
        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private static final Logger log = Logger.getLogger(ReservationServiceConcurrencyTest.class.getName());

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private EntityManagerFactory emf;

    @Autowired
    private ReservationService reservationService;

    @MockBean
    private ReservationRepository reservationRepository;

    @Autowired
    private SeatRepository seatRepository;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private CapacityService capacityService;

    @MockBean
    private QueuePermitService queuePermitService;

    private Long eventId;
    private Long zoneId;
    private Long seatId;
    private final java.util.Map<String, RLock> locks = new ConcurrentHashMap<>();

    private final List<Reservation> savedReservations = new CopyOnWriteArrayList<>();
    @BeforeEach
    void setUp() throws InterruptedException {
        locks.clear();
        when(redissonClient.getLock(anyString())).thenAnswer(invocation -> {
            String lockName = invocation.getArgument(0);
            return locks.computeIfAbsent(lockName, this::newLock);
        });
        when(capacityService.reserve(anyLong(), anyInt())).thenReturn(true);

        savedReservations.clear();
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            savedReservations.add(reservation);
            return reservation;
        });
        when(reservationRepository.count()).thenAnswer(invocation -> (long) savedReservations.size());
        when(reservationRepository.sumQuantityByUserIdAndEventIdAndStatusIn(anyLong(), anyLong(), anyList()))
                .thenAnswer(invocation -> activeTicketQuantity(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        TransactionTemplate tmpl = new TransactionTemplate(txManager);
        tmpl.execute(status -> {
            jakarta.persistence.EntityManager em = emf.createEntityManager();
            try {
                em.getTransaction().begin();
                Event event = new Event("Concert", "Arena", Instant.now(), null, 1L);
                Zone zone = new Zone("GA", 100, BigDecimal.TEN);
                for (int number = 1; number <= 5; number++) {
                    zone.addSeat(new Seat("A", number));
                }
                event.addZone(zone);
                em.persist(event);
                em.getTransaction().commit();
                eventId = event.getId();
                zoneId = zone.getId();
                seatId = zone.getSeats().get(0).getId();
            } finally {
                em.close();
            }
            return null;
        });
    }

    @Test
    void hammerSingleSeat_noDoubleBooking() throws InterruptedException {
        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        ReserveSeatsRequest request = new ReserveSeatsRequest(
                eventId, zoneId, List.of(seatId));

        for (int i = 0; i < threads; i++) {
            final long userId = i + 1;
            pool.submit(() -> {
                try {
                    start.await();
                    reservationService.reserve(userId, null, request);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                    if (errors.size() < 3) {
                        log.warning("reserve failed: " + e.getMessage());
                        e.printStackTrace();
                    }
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // exactly one reservation should succeed; the rest must fail cleanly
        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(threads - 1);

        long confirmedOrHeld = reservationRepository.count();
        assertThat(confirmedOrHeld).isEqualTo(1);

        Seat seat = seatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).isNotEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void concurrentStandingReservationsForSameUserCannotExceedActiveTicketCap() throws Exception {
        Long userId = 42L;
        CountDownLatch competingActiveTicketLockAttempted = new CountDownLatch(1);
        locks.put("active-tickets:" + userId + ":" + eventId,
                newActiveTicketLock(competingActiveTicketLockAttempted));
        List<Long> seatIds = seatRepository.findByZoneIdOrderByRowLabelAscNumberAsc(zoneId).stream().map(Seat::getId).toList();
        ReserveSeatsRequest threeSeats = new ReserveSeatsRequest(
                eventId, zoneId, seatIds.subList(0, 3), List.of("Ada", "Bea", "Cam"));
        ReserveSeatsRequest twoSeats = new ReserveSeatsRequest(
                eventId, zoneId, seatIds.subList(3, 5), List.of("Dee", "Eli"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        for (ReserveSeatsRequest request : List.of(threeSeats, twoSeats)) {
            pool.submit(() -> {
                try {
                    start.await();
                    reservationService.reserve(userId, null, request);
                    successes.incrementAndGet();
                } catch (Exception exception) {
                    failures.incrementAndGet();
                    errors.add(exception);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        assertThat(errors).hasSize(1).allSatisfy(error -> assertThat(error).isInstanceOf(IllegalArgumentException.class));
        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);
        assertThat(reservationRepository.sumQuantityByUserIdAndEventIdAndStatusIn(
                userId, eventId, List.of(ReservationStatus.HELD, ReservationStatus.CONFIRMED))).isLessThanOrEqualTo(4);
    }

    private RLock newLock(String lockName) {
        ReentrantLock lock = new ReentrantLock();
        RLock mockLock = org.mockito.Mockito.mock(RLock.class);
        try {
            when(mockLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenAnswer(invocation ->
                    lock.tryLock(invocation.getArgument(0), invocation.getArgument(2)));
        } catch (InterruptedException exception) {
            throw new AssertionError(exception);
        }
        when(mockLock.isHeldByCurrentThread()).thenAnswer(invocation -> lock.isHeldByCurrentThread());
        org.mockito.Mockito.doAnswer(invocation -> {
            lock.unlock();
            return null;
        }).when(mockLock).unlock();
        return mockLock;
    }

    private RLock newActiveTicketLock(CountDownLatch competingAttempted) {
        ReentrantLock lock = new ReentrantLock();
        AtomicBoolean firstAttempt = new AtomicBoolean(true);
        RLock mockLock = org.mockito.Mockito.mock(RLock.class);
        try {
            when(mockLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
                boolean first = firstAttempt.compareAndSet(true, false);
                if (!first) {
                    competingAttempted.countDown();
                }
                lock.lockInterruptibly();
                boolean acquired = true;
                if (first && acquired && !competingAttempted.await(5, TimeUnit.SECONDS)) {
                    lock.unlock();
                    throw new IllegalStateException("competing active-ticket lock was not attempted");
                }
                return acquired;
            });
        } catch (InterruptedException exception) {
            throw new AssertionError(exception);
        }
        when(mockLock.isHeldByCurrentThread()).thenAnswer(invocation -> lock.isHeldByCurrentThread());
        org.mockito.Mockito.doAnswer(invocation -> {
            lock.unlock();
            return null;
        }).when(mockLock).unlock();
        return mockLock;
    }

    private long activeTicketQuantity(Long userId, Long eventId, List<ReservationStatus> statuses) {
        return savedReservations.stream()
                .filter(reservation -> reservation.getUserId().equals(userId))
                .filter(reservation -> reservation.getEventId().equals(eventId))
                .filter(reservation -> statuses.contains(reservation.getStatus()))
                .mapToLong(reservation -> reservation.getSeatIds().size())
                .sum();
    }
}

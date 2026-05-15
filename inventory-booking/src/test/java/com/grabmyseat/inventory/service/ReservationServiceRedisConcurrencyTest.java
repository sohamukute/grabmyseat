package com.grabmyseat.inventory.service;

import com.grabmyseat.inventory.dto.ReserveSeatsRequest;
import com.grabmyseat.inventory.model.Event;
import com.grabmyseat.inventory.model.Seat;
import com.grabmyseat.inventory.model.SeatStatus;
import com.grabmyseat.inventory.model.Zone;
import com.grabmyseat.inventory.repository.EventRepository;
import com.grabmyseat.inventory.repository.ReservationRepository;
import com.grabmyseat.inventory.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ReservationServiceRedisConcurrencyTest {

    private static final Logger log = Logger.getLogger(ReservationServiceRedisConcurrencyTest.class.getName());

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private CapacityService capacityService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long eventId;
    private Long zoneId;
    private Long seatId;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        eventRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        Event event = new Event("Concert", "Arena", Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), 1L);
        Zone zone = new Zone("GA", 1, BigDecimal.TEN);
        Seat seat = new Seat("A", 1);
        zone.addSeat(seat);
        event.addZone(zone);
        event = eventRepository.save(event);

        zoneId = zone.getId();
        seatId = seat.getId();
        eventId = event.getId();

        capacityService.initialize(zoneId, 1);
    }

    @Test
    void concurrentReservations_onlyOneSucceeds() throws InterruptedException {
        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        ReserveSeatsRequest request = new ReserveSeatsRequest(eventId, zoneId, List.of(seatId));

        for (int i = 0; i < threads; i++) {
            final long userId = i + 1;
            pool.submit(() -> {
                try {
                    start.await();
                    reservationService.reserve(userId, null, request);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                    if (errors.size() < 5) {
                        log.warning("reserve failed: " + e.getMessage());
                    }
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(threads - 1);
        assertThat(reservationRepository.count()).isEqualTo(1);

        Seat seat = seatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).isNotEqualTo(SeatStatus.AVAILABLE);
        assertThat(capacityService.available(zoneId)).isZero();
    }
}

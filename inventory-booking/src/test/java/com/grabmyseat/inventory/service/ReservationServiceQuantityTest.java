package com.grabmyseat.inventory.service;

import com.grabmyseat.inventory.dto.ReservationResponse;
import com.grabmyseat.inventory.model.Event;
import com.grabmyseat.inventory.model.SaleType;
import com.grabmyseat.inventory.model.Seat;
import com.grabmyseat.inventory.model.Zone;
import com.grabmyseat.inventory.repository.EventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManagerFactory;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H2-backed (no Testcontainers required) coverage for the two reservation-contract
 * changes that the seat-less General Admission zone and the standard-sale queue
 * permit bypass depend on. {@link QueuePermitService} is mocked here purely to
 * observe whether {@code reserve()} decides to call it at all for a given sale
 * type; its own validation behaviour is exercised wherever it is unit-tested
 * directly.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:reservation-quantity;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({ReservationService.class, ReservationServiceQuantityTest.MeterRegistryConfig.class})
class ReservationServiceQuantityTest {

    @TestConfiguration
    static class MeterRegistryConfig {
        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private EntityManagerFactory emf;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private EventRepository eventRepository;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private CapacityService capacityService;

    @MockBean
    private QueuePermitService queuePermitService;

    private Long standardEventId;
    private Long standardZoneId;
    private Long lowStockZoneId;
    private Long flashEventId;
    private Long flashZoneId;
    @BeforeEach
    void setUp() {
        when(redissonClient.getLock(anyString())).thenAnswer(invocation -> newLock());
        when(capacityService.reserve(anyLong(), anyInt())).thenReturn(true);

        TransactionTemplate tmpl = new TransactionTemplate(txManager);
        tmpl.execute(status -> {
            jakarta.persistence.EntityManager em = emf.createEntityManager();
            try {
                em.getTransaction().begin();

                // "General Admission" is only distinguished from a seated zone by name
                // and display type in the API - it is backed by real Seat rows just
                // like every other zone, so quantity-based booking auto-allocates seats
                // exactly the same way for it as for a premium seated zone.
                Event standardEvent = new Event("Standing Show", "Arena", null,
                        Instant.now().plus(10, ChronoUnit.DAYS), null, Instant.now().minusSeconds(60),
                        Instant.now().minusSeconds(60), null, SaleType.STANDARD, 1L);
                Zone gaZone = new Zone("General Admission", 5, BigDecimal.TEN);
                for (int number = 1; number <= 5; number++) {
                    gaZone.addSeat(new Seat("GA", number));
                }
                standardEvent.addZone(gaZone);
                em.persist(standardEvent);

                Zone lowStockZone = new Zone("Left Premium", 2, BigDecimal.valueOf(20));
                lowStockZone.addSeat(new Seat("L", 1));
                lowStockZone.addSeat(new Seat("L", 2));
                standardEvent.addZone(lowStockZone);

                Event flashEvent = new Event("Flash Show", "Arena", null,
                        Instant.now().plus(10, ChronoUnit.DAYS), null, Instant.now().minusSeconds(60),
                        Instant.now().minusSeconds(60), null, SaleType.FLASH, 1L);
                Zone flashZone = new Zone("General Admission", 5, BigDecimal.TEN);
                for (int number = 1; number <= 5; number++) {
                    flashZone.addSeat(new Seat("GA", number));
                }
                flashEvent.addZone(flashZone);
                em.persist(flashEvent);

                em.getTransaction().commit();
                standardEventId = standardEvent.getId();
                standardZoneId = gaZone.getId();
                lowStockZoneId = lowStockZone.getId();
                flashEventId = flashEvent.getId();
                flashZoneId = flashZone.getId();
            } finally {
                em.close();
            }
            return null;
        });
    }

    @Test
    void autoAllocatesRequestedQuantityOfAvailableSeats() {
        ReservationResponse response = reservationService.reserveByQuantity(
                1L, "Asha Rao", null, standardEventId, standardZoneId, 3, List.of("Asha Rao", "Ben Rao", "Cid Rao"));

        assertThat(response.seatIds()).hasSize(3);
        assertThat(response.seatIds()).doesNotHaveDuplicates();
    }

    @Test
    void rejectsQuantityLargerThanAvailableSeats() {
        assertThatThrownBy(() -> reservationService.reserveByQuantity(
                1L, "Asha Rao", null, standardEventId, lowStockZoneId, 3, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enough seats available");
    }

    @Test
    void standardSaleSkipsQueuePermitValidation() {
        reservationService.reserveByQuantity(1L, "Asha Rao", null, standardEventId, standardZoneId, 1, List.of("Asha Rao"));

        verify(queuePermitService, never()).validate(anyString(), anyLong(), anyLong());
    }

    @Test
    void flashSaleStillRequiresQueuePermitValidation() {
        reservationService.reserveByQuantity(2L, "Ben Rao", "permit-token", flashEventId, flashZoneId, 1, List.of("Ben Rao"));

        verify(queuePermitService).validate(eq("permit-token"), eq(flashEventId), eq(2L));
    }

    private RLock newLock() {
        ReentrantLock delegate = new ReentrantLock();
        return (RLock) java.lang.reflect.Proxy.newProxyInstance(
                RLock.class.getClassLoader(), new Class<?>[]{RLock.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "tryLock":
                            return delegate.tryLock();
                        case "unlock":
                            if (delegate.isHeldByCurrentThread()) delegate.unlock();
                            return null;
                        case "isHeldByCurrentThread":
                            return delegate.isHeldByCurrentThread();
                        default:
                            return method.getReturnType() == boolean.class ? false : null;
                    }
                });
    }
}

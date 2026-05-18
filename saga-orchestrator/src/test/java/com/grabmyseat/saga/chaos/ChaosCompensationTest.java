package com.grabmyseat.saga.chaos;

import com.grabmyseat.saga.repository.SagaInstanceRepository;
import com.grabmyseat.saga.repository.SagaOutboxRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chaos test: simulate the payment-wallet service crashing mid-saga and verify
 * that SagaService compensation still releases the inventory seats even when
 * the refund step fails with a non-ClientException (connection refused).
 *
 * Scenario:
 *   1. debit wallet        — succeeds (wallet up)
 *   2. confirm inventory   — succeeds
 *   3. cancel booking      — triggers compensation
 *   4. credit wallet       — FAILS: wallet process killed mid-saga
 *   5. cancel inventory    — must succeed: seat release must run even if
 *                            the wallet refund step raised a
 *                            non-ClientException (WebClientRequestException).
 *
 * Before the SagaService exception-handling fix, step 4 would propagate as a
 * plain RuntimeException, roll back the @Transactional method, and the saga
 * instance + outbox would vanish without compensation. The seat would remain
 * confirmed, silently leaking capacity.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ChaosCompensationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("grabmyseat_saga_chaos_test")
            .withUsername("grabmyseat")
            .withPassword("grabmyseat");

    static MockWebServer inventoryServer;
    static MockWebServer walletServer;
    static MockWebServer ticketingServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SagaInstanceRepository sagaInstanceRepository;

    @Autowired
    private SagaOutboxRepository sagaOutboxRepository;

    @BeforeAll
    static void startServers() throws IOException {
        inventoryServer = new MockWebServer();
        walletServer = new MockWebServer();
        ticketingServer = new MockWebServer();
        inventoryServer.start();
        walletServer.start();
        ticketingServer.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("TEST_POSTGRES_URL", postgres::getJdbcUrl);
        registry.add("TEST_INVENTORY_URI", () -> inventoryServer.url("").toString().replaceAll("/$", ""));
        registry.add("TEST_WALLET_URI", () -> walletServer.url("").toString().replaceAll("/$", ""));
        registry.add("TEST_TICKETING_URI", () -> ticketingServer.url("").toString().replaceAll("/$", ""));
        registry.add("TEST_TICKETING_INTERNAL_API_KEY", () -> "test-ticketing-key");
    }

    @BeforeEach
    void setUp() {
        sagaOutboxRepository.deleteAll();
        sagaInstanceRepository.deleteAll();
        inventoryServer.setDispatcher(new QueueDispatcher());
        walletServer.setDispatcher(new QueueDispatcher());
        ticketingServer.setDispatcher(new QueueDispatcher());
    }

    @AfterAll
    static void stopServers() throws IOException {
        if (inventoryServer != null) {
            inventoryServer.shutdown();
        }
        if (walletServer != null) {
            walletServer.shutdown();
        }
        if (ticketingServer != null) {
            ticketingServer.shutdown();
        }
    }

    @Test
    void killPaymentMidSaga_seatsAreStillReleased() throws Exception {
        String token = "res-chaos-kill";

        // 1. debit: wallet is still up.
        walletServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "id": 1,
                          "walletAccountId": 1,
                          "type": "DEBIT",
                          "amount": 50.00,
                          "balanceAfter": 950.00,
                          "idempotencyKey": "%s",
                          "reference": "%s"
                        }
                        """.formatted(token, token)));

        // 2. inventory: getReservation + getZonePrice + confirmReservation succeed.
        inventoryServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "token": "%s",
                          "eventId": 1,
                          "zoneId": 10,
                          "seatIds": [100],
                          "totalPrice": 50.00,
                          "status": "HELD",
                          "expiresAt": "2099-01-01T00:00:00Z"
                        }
                        """.formatted(token)));
        inventoryServer.enqueue(new MockResponse().setResponseCode(200));
        ticketingServer.enqueue(new MockResponse().setResponseCode(201));

        mockMvc.perform(post("/api/saga/bookings/{token}/confirm", token)
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // *** CHAOS: kill the payment-wallet process mid-saga. ***
        walletServer.shutdown();

        // 3. cancel triggers compensation: credit wallet (killed → connection refused,
        //    a non-ClientException), then cancel inventory (still up).
        inventoryServer.enqueue(new MockResponse().setResponseCode(200));

        mockMvc.perform(post("/api/saga/bookings/{token}/cancel", token)
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPENSATED"))
                .andExpect(jsonPath("$.latestStep").value("RELEASED"));

        // The saga instance and outbox rows must persist (no rollback swallowing them).
        assertThat(sagaInstanceRepository.findByReservationToken(token)).isPresent();

        // Verify inventory cancel was actually called (seat release ran even though
        // the wallet refund step failed). Dequeue the earlier getReservation /
        // confirmReservation requests from the confirm phase first.
        RecordedRequest first = inventoryServer.takeRequest();
        assertThat(first.getPath()).isEqualTo("/api/inventory/reservations/" + token);
        RecordedRequest second = inventoryServer.takeRequest();
        assertThat(second.getPath()).isEqualTo("/api/inventory/reservations/" + token + "/confirm");
        RecordedRequest cancelRequest = inventoryServer.takeRequest();
        assertThat(cancelRequest.getPath()).isEqualTo("/api/inventory/reservations/" + token + "/cancel");
    }
}

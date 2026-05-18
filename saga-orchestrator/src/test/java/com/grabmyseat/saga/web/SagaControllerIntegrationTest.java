package com.grabmyseat.saga.web;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SagaControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("grabmyseat_saga_test")
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
        registry.add("ticketing.internal.api-key", () -> "test-ticketing-key");
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
    void confirm_happyPath() throws Exception {
        String token = "res-abc";

        inventoryServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "token": "res-abc",
                          "eventId": 1,
                          "zoneId": 10,
                          "seatIds": [100, 101],
                          "totalPrice": 100.00,
                          "status": "HELD",
                          "expiresAt": "2099-01-01T00:00:00Z"
                        }
                        """));
        inventoryServer.enqueue(new MockResponse().setResponseCode(200));
        walletServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "id": 1,
                          "walletAccountId": 1,
                          "type": "DEBIT",
                          "amount": 100.00,
                          "balanceAfter": 900.00,
                          "idempotencyKey": "res-abc",
                          "reference": "res-abc"
                        }
                        """));
        ticketingServer.enqueue(new MockResponse().setResponseCode(201));

        mockMvc.perform(post("/api/saga/bookings/{token}/confirm", token)
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationToken").value(token))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.latestStep").value("TICKET_ISSUED"));

        assertThat(sagaInstanceRepository.findByReservationToken(token)).isPresent();
        RecordedRequest ticketingRequest = ticketingServer.takeRequest();
        assertThat(ticketingRequest.getPath()).isEqualTo("/api/ticketing/internal/tickets");
        assertThat(ticketingRequest.getHeader("X-Internal-Api-Key")).isEqualTo("test-ticketing-key");
        assertThat(ticketingRequest.getHeader("X-User-Id")).isEqualTo("42");
    }

    @Test
    void confirm_insufficientFunds() throws Exception {
        String token = "res-def";

        inventoryServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "token": "res-def",
                          "eventId": 1,
                          "zoneId": 10,
                          "seatIds": [100],
                          "totalPrice": 50.00,
                          "status": "HELD",
                          "expiresAt": "2099-01-01T00:00:00Z"
                        }
                        """));
        inventoryServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "price": 50.00
                        }
                        """));
        walletServer.enqueue(new MockResponse()
                .setResponseCode(409)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "error": "INSUFFICIENT_FUNDS",
                          "message": "not enough funds",
                          "timestamp": "2099-01-01T00:00:00Z"
                        }
                        """));

        mockMvc.perform(post("/api/saga/bookings/{token}/confirm", token)
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"));

        assertThat(sagaInstanceRepository.findByReservationToken(token)).isPresent();
    }

    @Test
    void confirm_inventoryFailsAndWalletRefundFails_compensatesAndReleasesSeats() throws Exception {
        String token = "res-chaos";

        inventoryServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "token": "res-chaos",
                          "eventId": 1,
                          "zoneId": 10,
                          "seatIds": [100],
                          "totalPrice": 50.00,
                          "status": "HELD",
                          "expiresAt": "2099-01-01T00:00:00Z"
                        }
                        """));
        inventoryServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "error": "INVENTORY_ERROR",
                          "message": "inventory is down"
                        }
                        """));
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
                          "idempotencyKey": "res-chaos",
                          "reference": "res-chaos"
                        }
                        """));
        walletServer.enqueue(new MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "error": "WALLET_UNAVAILABLE",
                          "message": "wallet is down"
                        }
                        """));
        inventoryServer.enqueue(new MockResponse().setResponseCode(200));

        mockMvc.perform(post("/api/saga/bookings/{token}/confirm", token)
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationToken").value(token))
                .andExpect(jsonPath("$.status").value("COMPENSATED"))
                .andExpect(jsonPath("$.latestStep").value("RELEASED"));

        assertThat(sagaInstanceRepository.findByReservationToken(token)).isPresent();

        RecordedRequest first = inventoryServer.takeRequest();
        assertThat(first.getPath()).isEqualTo("/api/inventory/reservations/res-chaos");
        RecordedRequest second = inventoryServer.takeRequest();
        assertThat(second.getPath()).isEqualTo("/api/inventory/reservations/res-chaos/confirm");
        RecordedRequest cancelRequest = inventoryServer.takeRequest();
        assertThat(cancelRequest.getPath()).isEqualTo("/api/inventory/reservations/res-chaos/cancel");
    }

    @Test
    void status_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/saga/bookings/{token}/status", "res-xyz"))
                .andExpect(status().isUnauthorized());
    }
}

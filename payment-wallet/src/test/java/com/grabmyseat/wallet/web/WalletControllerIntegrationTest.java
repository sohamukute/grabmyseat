package com.grabmyseat.wallet.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class WalletControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("grabmyseat_wallet")
            .withUsername("grabmyseat")
            .withPassword("grabmyseat");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("TEST_POSTGRES_URL", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void adminTopUp_thenCustomerReadsBalance() throws Exception {
        String key = java.util.UUID.randomUUID().toString();
        String body = String.format("{\"userId\": 42, \"amount\": \"150.00\", \"idempotencyKey\": \"%s\"}", key);
        mvc.perform(post("/api/wallet/admin/topups")
                        .header("X-User-Id", "1")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150.00));

        mvc.perform(get("/api/wallet/me/balance")
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.balance").value(150.00));
    }

    @Test
    void duplicateTopUpIdempotencyKey_returnsSameBalance() throws Exception {
        String key = java.util.UUID.randomUUID().toString();
        String body = String.format("{\"userId\": 7, \"amount\": \"20.00\", \"idempotencyKey\": \"%s\"}", key);

        mvc.perform(post("/api/wallet/admin/topups")
                        .header("X-User-Id", "1")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(20.00));

        mvc.perform(post("/api/wallet/admin/topups")
                        .header("X-User-Id", "1")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(20.00));
    }

    @Test
    void nonAdminCannotTopUp() throws Exception {
        String body = "{\"userId\": 42, \"amount\": \"10.00\", \"idempotencyKey\": \"non-admin-key\"}";
        mvc.perform(post("/api/wallet/admin/topups")
                        .header("X-User-Id", "1")
                        .header("X-User-Roles", "ROLE_CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
    @Test
    void customerDemoTopUpCreditsOwnWallet_andAnonymousRequestIsUnauthorized() throws Exception {
        String key = java.util.UUID.randomUUID().toString();
        String body = String.format("{\"amount\": \"200.00\", \"idempotencyKey\": \"%s\"}", key);

        mvc.perform(post("/api/wallet/me/demo-topups")
                        .header("X-User-Id", "77")
                        .header("X-User-Roles", "ROLE_CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(200.00));

        mvc.perform(post("/api/wallet/me/demo-topups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}

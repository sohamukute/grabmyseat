package com.grabmyseat.auth;

import com.grabmyseat.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Testcontainers
class AuthFlowTest {

    // a real Postgres in a throwaway container, not a mock or H2, so the migration and the unique
    // constraint behave exactly as they will in production
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "test-secret-test-secret-test-secret-0123456789");
        registry.add("demo.admin.username", () -> "demo-admin");
        registry.add("demo.admin.password", () -> "DemoPass!2026");
        registry.add("demo.staff.username", () -> "demo-staff");
        registry.add("demo.staff.password", () -> "DemoPass!2026");
        registry.add("demo.organizer.username", () -> "north-star-live");
        registry.add("demo.organizer.password", () -> "OrganizerPass!2026");
        registry.add("auth.internal.api-key", () -> "test-internal-key");
        registry.add("auth.otp.expose-code", () -> true);
    }

    @Autowired
    UserRepository users;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void register_then_login_returns_tokens() throws Exception {
        register("alice", "password123");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("alice", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.tokenType", is("Bearer")));
    }

    @Test
    void duplicate_username_is_rejected() throws Exception {
        register("bob", "password123");

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("bob", "password123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("username already taken")));
    }

    @Test
    void wrong_password_is_unauthorized() throws Exception {
        register("carol", "password123");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("carol", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_needs_a_token_and_returns_the_user() throws Exception {
        String token = accessToken(register("dave", "password123"));

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("dave")))
                .andExpect(jsonPath("$.roles[0]", is("ROLE_CUSTOMER")));
    }

    @Test
    void customer_can_request_and_verify_a_mobile_code() throws Exception {
        String response = mvc.perform(post("/api/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+919876543210\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoCode").exists())
                .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+919876543210\",\"code\":\""
                                + json.readTree(response).get("demoCode").asText() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void administrator_can_search_the_user_directory() throws Exception {
        String response = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("demo-admin", "DemoPass!2026")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/auth/admin/users")
                        .param("query", "north")
                        .param("page", "0")
                        .header("Authorization", "Bearer " + accessToken(response)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].displayName", is("north-star-live")))
                .andExpect(jsonPath("$.content[0].roles[0]", is("ROLE_ORGANIZER")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void non_administrator_cannot_access_the_user_directory() throws Exception {
        String token = accessToken(register("directory-customer", "password123"));

        mvc.perform(get("/api/auth/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void short_password_fails_validation() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("erin", "short")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void organizer_registration_generates_username_and_grants_organizer_role() throws Exception {
        String body = """
                {"companyName":"North Star Events","companyEmail":"hello@northstar.example",
                 "companyPhone":"9876543210","password":"StrongPass#2026"}
                """;

        String response = mvc.perform(post("/api/auth/organizer/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", matchesPattern("north-star-events-[a-z0-9]{6}")))
                .andExpect(jsonPath("$.session.accessToken").exists())
                .andReturn().getResponse().getContentAsString();

        String accessToken = json.readTree(response).path("session").path("accessToken").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]", is("ROLE_ORGANIZER")));
    }

    @Test
    void demo_profile_seeds_expected_accounts() {
        org.junit.jupiter.api.Assertions.assertAll(
                () -> org.junit.jupiter.api.Assertions.assertTrue(users.existsByUsername("demo-admin")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(users.existsByUsername("demo-staff")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(users.existsByUsername("north-star-live")));
    }

    @Test
    void internal_display_lookup_by_id_returns_only_identity_fields() throws Exception {
        register("display-user", "password123");
        Long userId = users.findByUsername("display-user").orElseThrow().getId();

        mvc.perform(get("/api/auth/internal/users/id/{userId}", userId)
                        .header("X-Internal-Api-Key", "test-internal-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(userId.intValue())))
                .andExpect(jsonPath("$.username", is("display-user")))
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andExpect(jsonPath("$.phoneE164").doesNotExist());
    }

    @Test
    void organizer_registration_rejects_weak_password_and_duplicate_company_email() throws Exception {
        String valid = """
                {"companyName":"Everywhere Events","companyEmail":"team@everywhere.example",
                 "companyPhone":"9876543210","password":"StrongPass#2026"}
                """;
        mvc.perform(post("/api/auth/organizer/register")
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/organizer/register")
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/organizer/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Weak Event\",\"companyEmail\":\"weak@example.com\",\"companyPhone\":\"9876543210\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    private String register(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(username, password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String accessToken(String tokenResponseBody) throws Exception {
        return json.readTree(tokenResponseBody).get("accessToken").asText();
    }

    private String body(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }
}

package com.grabmyseat.waitingroom.web;

import com.grabmyseat.waitingroom.client.InventorySaleClient;
import com.grabmyseat.waitingroom.service.WaitlistService;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InternalReleaseControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("grabmyseat_waiting_room")
            .withUsername("grabmyseat")
            .withPassword("grabmyseat");

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("waiting-room.internal.api-key", () -> "test-internal-key");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private WaitlistService waitlistService;

    @MockBean
    private InventorySaleClient inventorySaleClient;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        org.mockito.Mockito.doNothing().when(inventorySaleClient).requireInterestAccess(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void release_withWaitlistedUsers_returnsNotifiedUserIds() throws Exception {
        waitlistService.join(1L, 100L, 200L);
        waitlistService.join(2L, 100L, 200L);

        mvc.perform(post("/api/waiting-room/internal/events/{eventId}/zones/{zoneId}/release", 100L, 200L)
                        .header("X-Internal-Api-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void release_noWaitlisters_returnsEmptyList() throws Exception {
        mvc.perform(post("/api/waiting-room/internal/events/{eventId}/zones/{zoneId}/release", 100L, 200L)
                        .header("X-Internal-Api-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void release_withoutApiKey_returnsUnauthorized() throws Exception {
        mvc.perform(post("/api/waiting-room/internal/events/{eventId}/zones/{zoneId}/release", 100L, 200L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\": 1}"))
                .andExpect(status().isUnauthorized());
    }
}

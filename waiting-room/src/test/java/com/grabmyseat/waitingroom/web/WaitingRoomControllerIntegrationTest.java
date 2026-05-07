package com.grabmyseat.waitingroom.web;

import com.grabmyseat.waitingroom.client.InventorySaleClient;
import com.grabmyseat.waitingroom.service.AdmissionService;
import com.grabmyseat.waitingroom.service.QueueService;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WaitingRoomControllerIntegrationTest {

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
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private QueueService queueService;

    @Autowired
    private AdmissionService admissionService;

    @MockBean
    private InventorySaleClient inventorySaleClient;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        org.mockito.Mockito.doNothing().when(inventorySaleClient).requireQueueAccess(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void join_withoutUserId_returnsUnauthorized() throws Exception {
        mvc.perform(post("/api/waiting-room/events/{eventId}/join", 100L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void join_withUserId_returnsTokenAndPosition() throws Exception {
        mvc.perform(post("/api/waiting-room/events/{eventId}/join", 100L)
                        .header("X-User-Id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.position").value(0))
                .andExpect(jsonPath("$.estimatedWaitSeconds").value(0));
    }

    @Test
    void position_waitingReturnsStatusAndRank() throws Exception {
        String token = mvc.perform(post("/api/waiting-room/events/{eventId}/join", 100L)
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"")[3];

        mvc.perform(get("/api/waiting-room/events/{eventId}/position", 100L)
                        .header("X-User-Id", "1")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.position").value(0))
                .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    void position_admittedReturnsAdmittedStatus() throws Exception {
        String token = mvc.perform(post("/api/waiting-room/events/{eventId}/join", 100L)
                        .header("X-User-Id", "1"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"")[3];

        queueService.join(2L, 100L);
        queueService.join(3L, 100L);
        admissionServiceAdmit(100L);

        mvc.perform(get("/api/waiting-room/events/{eventId}/position", 100L)
                        .header("X-User-Id", "1")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADMITTED"))
                .andExpect(jsonPath("$.position").value(0));
    }

    @Test
    void leave_removesToken() throws Exception {
        String token = mvc.perform(post("/api/waiting-room/events/{eventId}/join", 100L)
                        .header("X-User-Id", "1"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"")[3];

        mvc.perform(post("/api/waiting-room/events/{eventId}/leave", 100L)
                        .header("X-User-Id", "1")
                        .param("token", token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/waiting-room/events/{eventId}/position", 100L)
                        .header("X-User-Id", "1")
                        .param("token", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void permit_admittedToken_returnsPermitToken() throws Exception {
        String token = mvc.perform(post("/api/waiting-room/events/{eventId}/join", 100L)
                        .header("X-User-Id", "1"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"")[3];

        queueService.join(2L, 100L);
        queueService.join(3L, 100L);
        admissionServiceAdmit(100L);

        mvc.perform(get("/api/waiting-room/events/{eventId}/permit", 100L)
                        .header("X-User-Id", "1")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitToken").isNotEmpty());
    }

    @Test
    void permit_waitingToken_returnsForbidden() throws Exception {
        String token = mvc.perform(post("/api/waiting-room/events/{eventId}/join", 100L)
                        .header("X-User-Id", "1"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"")[3];

        mvc.perform(get("/api/waiting-room/events/{eventId}/permit", 100L)
                        .header("X-User-Id", "1")
                        .param("token", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void stream_returnsSseEmitter() throws Exception {
        String token = mvc.perform(post("/api/waiting-room/events/{eventId}/join", 100L)
                        .header("X-User-Id", "1"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"")[3];

        mvc.perform(get("/api/waiting-room/events/{eventId}/stream", 100L)
                        .header("X-User-Id", "1")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    void anotherAccountCannotReadOrUseAQueueToken() throws Exception {
        String token = mvc.perform(post("/api/waiting-room/events/{eventId}/join", 100L)
                        .header("X-User-Id", "1"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"")[3];

        mvc.perform(get("/api/waiting-room/events/{eventId}/position", 100L)
                        .header("X-User-Id", "2")
                        .param("token", token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/waiting-room/events/{eventId}/permit", 100L)
                        .header("X-User-Id", "2")
                        .param("token", token))
                .andExpect(status().isForbidden());
    }

    private void admissionServiceAdmit(Long eventId) {
        admissionService.admitNext(eventId, queueService);
    }
}

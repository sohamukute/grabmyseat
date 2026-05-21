package com.grabmyseat.ticketing.web;

import com.grabmyseat.ticketing.client.StaffAssignmentClient;
import com.grabmyseat.ticketing.dto.TicketRequest;
import com.grabmyseat.ticketing.dto.TicketResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class TicketControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("grabmyseat_ticketing")
            .withUsername("grabmyseat")
            .withPassword("grabmyseat");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("ticketing.internal.api-key", () -> "test-ticketing-key");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private StaffAssignmentClient staffAssignmentClient;

    @Test
    void issueAndGetTicket_flow() {
        TicketRequest request = new TicketRequest("res-token-1", 1L, 2L, 42L, List.of(10L, 11L), BigDecimal.valueOf(99.99));

        HttpHeaders headers = internalIssueHeaders("42");
        HttpEntity<TicketRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<TicketResponse> issueResponse = restTemplate.postForEntity(
                "/api/ticketing/internal/tickets", entity, TicketResponse.class);

        assertThat(issueResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TicketResponse ticket = issueResponse.getBody();
        assertThat(ticket).isNotNull();
        assertThat(ticket.reservationToken()).isEqualTo("res-token-1");
        assertThat(ticket.userId()).isEqualTo(42L);
        assertThat(ticket.seatIds()).containsExactly(10L, 11L);
        assertThat(ticket.qrPayload()).isNotBlank();

        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.set("X-User-Id", "42");
        ResponseEntity<TicketResponse> getResponse = restTemplate.exchange(
                "/api/ticketing/tickets/{token}",
                HttpMethod.GET,
                new HttpEntity<>(getHeaders),
                TicketResponse.class,
                ticket.reservationToken());

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().id()).isEqualTo(ticket.id());
    }

    @Test
    void issueWithoutApiKey_returnsUnauthorized() {
        TicketRequest request = new TicketRequest("res-token-2", 1L, 2L, 42L, List.of(10L), BigDecimal.TEN);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "42");
        ResponseEntity<TicketResponse> response = restTemplate.postForEntity(
                "/api/ticketing/internal/tickets", new HttpEntity<>(request, headers), TicketResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void issueWithoutUserId_returnsUnauthorized() {
        TicketRequest request = new TicketRequest("res-token-3", 1L, 2L, 42L, List.of(10L), BigDecimal.TEN);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Api-Key", "test-ticketing-key");
        ResponseEntity<TicketResponse> response = restTemplate.postForEntity(
                "/api/ticketing/internal/tickets", new HttpEntity<>(request, headers), TicketResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getTicket_byOtherUser_returnsForbidden() {
        TicketRequest request = new TicketRequest("res-token-4", 1L, 2L, 42L, List.of(10L), BigDecimal.TEN);
        HttpHeaders ownerHeaders = internalIssueHeaders("42");
        ResponseEntity<TicketResponse> issueResponse = restTemplate.postForEntity(
                "/api/ticketing/internal/tickets",
                new HttpEntity<>(request, ownerHeaders),
                TicketResponse.class);
        assertThat(issueResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        HttpHeaders otherHeaders = new HttpHeaders();
        otherHeaders.set("X-User-Id", "99");
        ResponseEntity<TicketResponse> getResponse = restTemplate.exchange(
                "/api/ticketing/tickets/{token}",
                HttpMethod.GET,
                new HttpEntity<>(otherHeaders),
                TicketResponse.class,
                issueResponse.getBody().reservationToken());

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void validateTicket_byGate_succeeds() {
        TicketRequest request = new TicketRequest("res-token-5", 1L, 2L, 42L, List.of(10L), BigDecimal.TEN);
        HttpHeaders ownerHeaders = internalIssueHeaders("42");
        ResponseEntity<TicketResponse> issueResponse = restTemplate.postForEntity(
                "/api/ticketing/internal/tickets",
                new HttpEntity<>(request, ownerHeaders),
                TicketResponse.class);
        assertThat(issueResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String token = issueResponse.getBody().reservationToken();

        HttpHeaders gateHeaders = new HttpHeaders();
        gateHeaders.set("X-User-Id", "1");
        gateHeaders.set("X-User-Roles", "ROLE_STAFF");
        when(staffAssignmentClient.isAuthorizedScanner(1L, 1L)).thenReturn(true);

        ResponseEntity<TicketResponse> validateResponse = restTemplate.postForEntity(
                "/api/ticketing/tickets/{token}/validate",
                new HttpEntity<>(gateHeaders),
                TicketResponse.class,
                token);

        assertThat(validateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(validateResponse.getBody()).isNotNull();
        assertThat(validateResponse.getBody().usedAt()).isNull();
        assertThat(validateResponse.getBody().attendance()).containsEntry("Guest", "PENDING");
    }

    @Test
    void validateTicket_byRegularUser_forbidden() {
        TicketRequest request = new TicketRequest("res-token-6", 1L, 2L, 42L, List.of(10L), BigDecimal.TEN);
        HttpHeaders ownerHeaders = internalIssueHeaders("42");
        ResponseEntity<TicketResponse> issueResponse = restTemplate.postForEntity(
                "/api/ticketing/internal/tickets",
                new HttpEntity<>(request, ownerHeaders),
                TicketResponse.class);
        assertThat(issueResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String token = issueResponse.getBody().reservationToken();

        HttpHeaders regularHeaders = new HttpHeaders();
        regularHeaders.set("X-User-Id", "42");
        regularHeaders.set("X-User-Roles", "ROLE_CUSTOMER");
        ResponseEntity<TicketResponse> validateResponse = restTemplate.postForEntity(
                "/api/ticketing/tickets/{token}/validate",
                new HttpEntity<>(regularHeaders),
                TicketResponse.class,
                token);

        assertThat(validateResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void validateTicket_thenCheckInMutatesAndSecondValidateReturnsConflict() {
        TicketRequest request = new TicketRequest("res-token-7", 1L, 2L, 42L, List.of(10L), BigDecimal.TEN);
        HttpHeaders ownerHeaders = internalIssueHeaders("42");
        ResponseEntity<TicketResponse> issueResponse = restTemplate.postForEntity(
                "/api/ticketing/internal/tickets",
                new HttpEntity<>(request, ownerHeaders),
                TicketResponse.class);
        assertThat(issueResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String token = issueResponse.getBody().reservationToken();

        HttpHeaders gateHeaders = new HttpHeaders();
        gateHeaders.set("X-User-Id", "1");
        gateHeaders.set("X-User-Roles", "ROLE_STAFF");
        HttpEntity<?> gateEntity = new HttpEntity<>(gateHeaders);
        when(staffAssignmentClient.isAuthorizedScanner(1L, 1L)).thenReturn(true);

        ResponseEntity<TicketResponse> first = restTemplate.postForEntity(
                "/api/ticketing/tickets/{token}/validate", gateEntity, TicketResponse.class, token);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().usedAt()).isNull();
        assertThat(first.getBody().attendance()).containsEntry("Guest", "PENDING");

        HttpHeaders checkInHeaders = new HttpHeaders();
        checkInHeaders.putAll(gateHeaders);
        checkInHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<TicketResponse> checkIn = restTemplate.postForEntity(
                "/api/ticketing/tickets/{token}/check-in",
                new HttpEntity<>("{\"attendeesPresent\":[\"Guest\"]}", checkInHeaders),
                TicketResponse.class,
                token);
        assertThat(checkIn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(checkIn.getBody()).isNotNull();
        assertThat(checkIn.getBody().usedAt()).isNotNull();
        assertThat(checkIn.getBody().attendance()).containsEntry("Guest", "ADMITTED");

        ResponseEntity<String> second = restTemplate.postForEntity(
                "/api/ticketing/tickets/{token}/validate", gateEntity, String.class, token);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private HttpHeaders internalIssueHeaders(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Api-Key", "test-ticketing-key");
        headers.set("X-User-Id", userId);
        return headers;
    }
}

package com.grabmyseat.ticketing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.grabmyseat.ticketing.repository.TicketRepository;
import com.grabmyseat.ticketing.model.Ticket;
import com.grabmyseat.ticketing.model.AttendanceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    private static final String SIGNING_KEY = "test-signing-key-that-is-long-enough";

    @Mock
    private TicketRepository ticketRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, objectMapper, SIGNING_KEY, 300, new SimpleMeterRegistry());
    }

    @Test
    void issue_savesTicketWithSignedPayload() {
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ticket ticket = ticketService.issue(1L, "res-123", 10L, 20L, List.of(100L, 101L), BigDecimal.valueOf(49.99));

        assertThat(ticket.getReservationToken()).isEqualTo("res-123");
        assertThat(ticket.getEventId()).isEqualTo(10L);
        assertThat(ticket.getZoneId()).isEqualTo(20L);
        assertThat(ticket.getUserId()).isEqualTo(1L);
        assertThat(ticket.getSeatIds()).containsExactly(100L, 101L);
        assertThat(ticket.getQrPayload()).isNotBlank();
        assertThat(ticket.getCreatedAt()).isNotNull();
        assertThat(ticket.getUsedAt()).isNull();

        verify(ticketRepository).save(ticket);
    }

    @Test
    void issue_payloadContainsAllFields() throws Exception {
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ticket ticket = ticketService.issue(1L, "res-123", 10L, 20L, List.of(100L), BigDecimal.valueOf(9.99));

        ObjectNode payload = (ObjectNode) objectMapper.readTree(ticket.getQrPayload());
        assertThat(payload.has("t")).isTrue();
        assertThat(payload.get("e").asLong()).isEqualTo(10L);
        assertThat(payload.get("z").asLong()).isEqualTo(20L);
        assertThat(payload.get("u").asLong()).isEqualTo(1L);
        assertThat(payload.get("p").asText()).isEqualTo("9.99");
        assertThat(payload.has("rotatedAt")).isTrue();
        assertThat(payload.has("exp")).isTrue();
        assertThat(payload.get("s").isArray()).isTrue();
        assertThat(payload.has("sig")).isTrue();
    }

    @Test
    void issue_duplicateReservationToken_throwsIllegalState() {
        when(ticketRepository.save(any(Ticket.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> ticketService.issue(1L, "res-123", 10L, 20L, List.of(100L), BigDecimal.TEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ticket already issued");
    }

    @Test
    void findByToken_returnsTicket() {
        Ticket ticket = new Ticket("res-123", 10L, 20L, 1L, List.of(100L), "{}");
        when(ticketRepository.findByReservationToken("res-123")).thenReturn(Optional.of(ticket));

        Optional<Ticket> result = ticketService.findByToken("res-123");

        assertThat(result).isPresent().contains(ticket);
    }

    @Test
    void findByToken_missing_returnsEmpty() {
        when(ticketRepository.findByReservationToken("missing")).thenReturn(Optional.empty());

        Optional<Ticket> result = ticketService.findByToken("missing");

        assertThat(result).isEmpty();
    }

    @Test
    void verify_freshTicket_returnsWithoutMutatingState() {
        String payload = ticketService.buildSignedPayload("tid", "res-123", 10L, 20L, 1L,
                List.of(100L, 101L), List.of("Alice", "Bob"), BigDecimal.TEN,
                Instant.now().plusSeconds(3600));
        Ticket ticket = new Ticket("res-123", 10L, 20L, 1L, List.of(100L, 101L),
                List.of("Alice", "Bob"), payload);
        when(ticketRepository.findByReservationToken("res-123")).thenReturn(Optional.of(ticket));

        Ticket verified = ticketService.verify("res-123");

        assertThat(verified).isSameAs(ticket);
        assertThat(verified.getUsedAt()).isNull();
        assertThat(verified.getAttendance())
                .containsEntry("Alice", AttendanceStatus.PENDING)
                .containsEntry("Bob", AttendanceStatus.PENDING);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void verify_alreadyUsed_throws() {
        String payload = ticketService.buildSignedPayload("tid", 10L, 20L, 1L, List.of(100L), BigDecimal.TEN,
                Instant.now().plusSeconds(3600));
        Ticket ticket = new Ticket("res-123", 10L, 20L, 1L, List.of(100L), payload);
        ticket.setUsedAt(Instant.now());
        when(ticketRepository.findByReservationToken("res-123")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.verify("res-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already used");
    }

    @Test
    void verify_invalidSignature_throws() {
        Ticket ticket = new Ticket("res-123", 10L, 20L, 1L, List.of(100L), "{\"t\":\"x\",\"sig\":\"bad\"}");
        when(ticketRepository.findByReservationToken("res-123")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.verify("res-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ticket signature");
    }

    @Test
    void verify_tamperedPayload_throws() throws Exception {
        String payload = ticketService.buildSignedPayload("tid", "res-123", 10L, 20L, 1L,
                List.of(100L), List.of("Guest"), BigDecimal.TEN, Instant.now().plusSeconds(3600));
        ObjectNode tamperedPayload = (ObjectNode) objectMapper.readTree(payload);
        tamperedPayload.put("e", 99L);
        Ticket ticket = new Ticket("res-123", 10L, 20L, 1L, List.of(100L), tamperedPayload.toString());
        when(ticketRepository.findByReservationToken("res-123")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.verify("res-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ticket signature");
    }

    @Test
    void verify_missingSignature_throws() {
        Ticket ticket = new Ticket("res-123", 10L, 20L, 1L, List.of(100L), "{\"t\":\"x\"}");
        when(ticketRepository.findByReservationToken("res-123")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.verify("res-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing ticket signature");
    }

    @Test
    void verify_expired_throws() {
        Instant past = Instant.now().minusSeconds(400);
        String payload = ticketService.buildSignedPayload("tid", 10L, 20L, 1L, List.of(100L), BigDecimal.TEN, past);
        Ticket ticket = new Ticket("res-123", 10L, 20L, 1L, List.of(100L), payload);
        when(ticketRepository.findByReservationToken("res-123")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.verify("res-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void regenerateQr_createsNewPayloadAndRotationTimestamp() {
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        Ticket ticket = ticketService.issue(1L, "res-123", 10L, 20L, List.of(100L), BigDecimal.valueOf(9.99));
        String originalPayload = ticket.getQrPayload();
        Instant originalRotatedAt = ticket.getQrRotatedAt();

        when(ticketRepository.findByReservationToken("res-123")).thenReturn(Optional.of(ticket));

        Ticket regenerated = ticketService.regenerateQr("res-123");

        assertThat(regenerated.getQrPayload()).isNotEqualTo(originalPayload);
        assertThat(regenerated.getQrRotatedAt()).isAfter(originalRotatedAt);
    }
}

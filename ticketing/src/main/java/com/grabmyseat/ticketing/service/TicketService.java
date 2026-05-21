package com.grabmyseat.ticketing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.grabmyseat.ticketing.model.Ticket;
import com.grabmyseat.ticketing.model.AttendanceStatus;
import com.grabmyseat.ticketing.repository.TicketRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final TicketRepository ticketRepository;
    private final ObjectMapper objectMapper;
    private final byte[] signingKey;
    private final int qrTtlSeconds;
    private final Counter ticketsIssuedCounter;

    public TicketService(TicketRepository ticketRepository,
                         ObjectMapper objectMapper,
                         @Value("${ticket.signing-key}") String signingKey,
                         @Value("${ticket.qr-ttl-seconds:300}") int qrTtlSeconds,
                         MeterRegistry meterRegistry) {
        this.ticketRepository = ticketRepository;
        this.objectMapper = objectMapper;
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
        this.qrTtlSeconds = qrTtlSeconds;
        this.ticketsIssuedCounter = meterRegistry == null ? null :
                Counter.builder("grabmyseat.tickets.issued")
                        .description("Total number of tickets issued")
                        .register(meterRegistry);
    }

    @Transactional
    public Ticket issue(Long userId, String reservationToken, Long eventId, Long zoneId,
                        List<Long> seatIds, List<String> attendeeNames, BigDecimal price) {
        Instant now = Instant.now();
        String ticketId = UUID.randomUUID().toString();

        List<String> names = attendeeNames == null || attendeeNames.isEmpty() ? List.of("Guest") : attendeeNames;
        String payload = buildSignedPayload(ticketId, reservationToken, eventId, zoneId, userId, seatIds, names, price, now);
        Ticket ticket = new Ticket(reservationToken, eventId, zoneId, userId, seatIds, names, payload);
        ticket.setQrRotatedAt(now);

        try {
            Ticket saved = ticketRepository.save(ticket);
            if (ticketsIssuedCounter != null) {
                ticketsIssuedCounter.increment();
            }
            return saved;
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate ticket issuance attempted for reservation token {}", reservationToken);
            throw new IllegalStateException("Ticket already issued for reservation token: " + reservationToken);
        }
    }

    public Ticket issue(Long userId, String reservationToken, Long eventId, Long zoneId,
                        List<Long> seatIds, BigDecimal price) {
        return issue(userId, reservationToken, eventId, zoneId, seatIds, List.of("Guest"), price);
    }

    @Transactional(readOnly = true)
    public Optional<Ticket> findByToken(String token) {
        return ticketRepository.findByReservationToken(token);
    }

    @Transactional(readOnly = true)
    public List<Ticket> findByUserId(Long userId) {
        return ticketRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Ticket verify(String token) {
        Ticket ticket = ticketRepository.findByReservationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + token));
        if (ticket.getUsedAt() != null) {
            throw new IllegalStateException("Ticket already used: " + token);
        }
        verifySignature(ticket.getQrPayload());
        return ticket;
    }

    @Transactional
    public Ticket checkIn(String token, List<String> attendeesPresent) {
        Ticket ticket = ticketRepository.findByReservationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + token));

        if (ticket.getUsedAt() != null) {
            throw new IllegalStateException("Ticket already used: " + token);
        }

        verifySignature(ticket.getQrPayload());

        List<String> present = attendeesPresent == null ? List.of() : attendeesPresent.stream().map(String::trim).toList();
        if (!ticket.getAttendeeNames().containsAll(present) || present.size() != new java.util.LinkedHashSet<>(present).size()) {
            throw new IllegalArgumentException("attendance list does not match this group ticket");
        }
        ticket.getAttendance().replaceAll((name, status) -> present.contains(name)
                ? AttendanceStatus.ADMITTED : AttendanceStatus.ABSENT);

        ticket.setUsedAt(Instant.now());
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket regenerateQr(String reservationToken) {
        Ticket ticket = ticketRepository.findByReservationToken(reservationToken)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + reservationToken));

        if (ticket.getUsedAt() != null) {
            throw new IllegalStateException("Ticket already used: " + reservationToken);
        }

        Instant now = Instant.now();
        Instant rotatedAt = ticket.getQrRotatedAt() == null || now.isAfter(ticket.getQrRotatedAt())
                ? now
                : ticket.getQrRotatedAt().plusMillis(1);
        String payload = buildSignedPayload(extractTicketId(ticket.getQrPayload()), ticket.getReservationToken(), ticket.getEventId(),
                ticket.getZoneId(), ticket.getUserId(), ticket.getSeatIds(), ticket.getAttendeeNames(),
                extractPrice(ticket.getQrPayload()), rotatedAt);
        ticket.setQrPayload(payload);
        ticket.setQrRotatedAt(rotatedAt);
        return ticketRepository.save(ticket);
    }

    String buildSignedPayload(String ticketId, String reservationToken, Long eventId, Long zoneId, Long userId,
                              List<Long> seatIds, List<String> attendeeNames, BigDecimal price, Instant rotatedAt) {
        Instant expiry = rotatedAt.plusSeconds(qrTtlSeconds);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("t", ticketId);
        node.put("r", reservationToken);
        node.put("e", eventId);
        node.put("z", zoneId);
        node.put("u", userId);
        node.put("h", attendeeNames.getFirst());
        node.set("a", objectMapper.valueToTree(attendeeNames));
        node.put("p", price.toPlainString());
        node.put("rotatedAt", rotatedAt.toEpochMilli());
        node.put("exp", expiry.toEpochMilli());
        node.set("s", objectMapper.valueToTree(seatIds));

        String canonical = canonicalJson(node);
        String signature = base64Url(hmac(canonical));

        ObjectNode signed = node.deepCopy();
        signed.put("sig", signature);
        return signed.toString();
    }

    // Retained for existing integrations; newly issued tickets always include the reservation token.
    String buildSignedPayload(String ticketId, Long eventId, Long zoneId, Long userId,
                              List<Long> seatIds, BigDecimal price, Instant rotatedAt) {
        return buildSignedPayload(ticketId, "", eventId, zoneId, userId, seatIds, List.of("Guest"), price, rotatedAt);
    }

    void verifySignature(String qrPayload) {
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(qrPayload);
            String signature = node.has("sig") ? node.get("sig").asText() : null;
            if (signature == null || signature.isBlank()) {
                throw new IllegalArgumentException("Missing ticket signature");
            }
            node.remove("sig");

            String canonical = canonicalJson(node);
            String expected = base64Url(hmac(canonical));

            if (!constantTimeEquals(signature.getBytes(StandardCharsets.UTF_8),
                    expected.getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("Invalid ticket signature");
            }

            long rotatedAtMillis = node.has("rotatedAt") ? node.get("rotatedAt").asLong(0L) : 0L;
            if (rotatedAtMillis <= 0) {
                throw new IllegalArgumentException("Missing ticket rotation timestamp");
            }
            Instant rotatedAt = Instant.ofEpochMilli(rotatedAtMillis);
            Instant expiry = rotatedAt.plusSeconds(qrTtlSeconds);
            if (Instant.now().isAfter(expiry)) {
                throw new IllegalStateException("Ticket QR has expired; regenerate to enter");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid QR payload", e);
        }
    }

    private String extractTicketId(String qrPayload) {
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(qrPayload);
            return node.get("t").asText();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid QR payload", e);
        }
    }

    private BigDecimal extractPrice(String qrPayload) {
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(qrPayload);
            return new BigDecimal(node.get("p").asText());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid QR payload", e);
        }
    }

    private String canonicalJson(ObjectNode node) {
        return node.toString();
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to sign ticket payload", e);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }
}

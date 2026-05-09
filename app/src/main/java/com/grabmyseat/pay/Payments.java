package com.grabmyseat.pay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grabmyseat.auth.web.ApiException;
import com.grabmyseat.booking.BookingResponse;
import com.grabmyseat.booking.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class Payments {

    private static final Logger log = LoggerFactory.getLogger(Payments.class);

    private final JdbcClient jdbc;
    private final Razorpay razorpay;
    private final BookingService bookings;
    private final ObjectMapper json;

    public Payments(JdbcClient jdbc, Razorpay razorpay, BookingService bookings, ObjectMapper json) {
        this.jdbc = jdbc;
        this.razorpay = razorpay;
        this.bookings = bookings;
        this.json = json;
    }

    public PayOrder order(long userId, long bookingId) {
        bookings.bookingOf(userId, bookingId);
        Held held = held(bookingId);
        if (!"HELD".equals(held.payStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "This booking has nothing left to pay.");
        }
        if (held.heldUntil().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Your 10 minute hold ran out. Book again to get seats.");
        }
        String orderId = held.orderId();
        if (orderId == null) {
            String created = razorpay.createOrder(held.amountPaise(), "booking-" + bookingId, bookingId);
            jdbc.sql("UPDATE booking SET order_id = ? WHERE id = ? AND order_id IS NULL").params(created, bookingId).update();
            orderId = held(bookingId).orderId();
        }
        return new PayOrder(orderId, razorpay.keyId(), held.amountPaise(), held.heldUntil(), held.email());
    }

    public BookingResponse confirm(long userId, long bookingId, ConfirmRequest request) {
        bookings.bookingOf(userId, bookingId);
        if (!request.orderId().equals(held(bookingId).orderId()) || !razorpay.checkoutSigned(request.orderId(), request.paymentId(), request.signature())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "We could not verify that payment. If money left your account, it will be refunded.");
        }
        if ("LATE".equals(settle(request.orderId(), request.paymentId()))) {
            throw new ApiException(HttpStatus.CONFLICT, "Your hold ran out before the payment arrived. We are refunding it in full.");
        }
        return bookings.booking(userId, bookingId);
    }

    public void webhook(String body, String signature, String eventId) {
        if (!razorpay.webhookSigned(body, signature)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad signature.");
        }
        int fresh = jdbc.sql("INSERT INTO payment_event (event_id) VALUES (?) ON CONFLICT DO NOTHING").param(eventId).update();
        if (fresh == 0) {
            return;
        }
        try {
            JsonNode event = json.readTree(body);
            String type = event.path("event").asText();
            JsonNode payment = event.path("payload").path("payment").path("entity");
            if ((type.equals("payment.captured") || type.equals("order.paid") || type.equals("payment.authorized"))
                    && !payment.path("order_id").asText().isEmpty()) {
                settle(payment.path("order_id").asText(), payment.path("id").asText());
            }
        } catch (IOException unreadable) {
            log.warn("webhook {} unreadable", eventId);
        }
    }

    String settle(String orderId, String paymentId) {
        Optional<Long> bookingId = jdbc.sql("SELECT id FROM booking WHERE order_id = ?").param(orderId).query(Long.class).optional();
        if (bookingId.isEmpty()) {
            return "UNKNOWN";
        }
        Held held = held(bookingId.get());
        JsonNode payment = razorpay.payment(paymentId);
        if (!orderId.equals(payment.path("order_id").asText()) || payment.path("amount").asLong() != held.amountPaise()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That payment does not match this booking.");
        }
        String status = payment.path("status").asText();
        if (status.equals("authorized")) {
            status = razorpay.capture(paymentId, held.amountPaise()).path("status").asText();
        }
        if (!status.equals("captured")) {
            throw new ApiException(HttpStatus.CONFLICT, "The payment did not go through. Try again.");
        }
        return bookings.markPaid(bookingId.get(), paymentId);
    }

    @Scheduled(fixedDelay = 15_000)
    public void expire() {
        List<Expired> expired = jdbc.sql("""
                        SELECT id, order_id FROM booking
                        WHERE pay_status = 'HELD' AND held_until < now() ORDER BY held_until LIMIT 50
                        """)
                .query(Expired.class).list();
        for (Expired booking : expired) {
            try {
                if (booking.orderId() != null && paidAfterAll(booking.orderId())) {
                    continue;
                }
                bookings.release(booking.id());
            } catch (RuntimeException failed) {
                log.warn("hold {} not released", booking.id());
            }
        }
    }

    @Scheduled(fixedDelay = 20_000)
    public void refunds() {
        try {
            int sent = 0;
            while (sent < 20 && refundNext()) {
                sent++;
            }
        } catch (RuntimeException stopped) {
            log.error("refunds stopped", stopped);
        }
    }

    private boolean paidAfterAll(String orderId) {
        for (JsonNode payment : razorpay.orderPayments(orderId)) {
            String status = payment.path("status").asText();
            if (status.equals("captured") || status.equals("authorized")) {
                settle(orderId, payment.path("id").asText());
                return true;
            }
        }
        return false;
    }

    private boolean refundNext() {
        Optional<Refund> next = jdbc.sql("""
                        UPDATE refund SET status = 'SENDING'
                        WHERE id = (SELECT id FROM refund
                                    WHERE status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= now())
                                    ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED)
                        RETURNING id, payment_id, amount_paise
                        """)
                .query(Refund.class).optional();
        if (next.isEmpty()) {
            return false;
        }
        Refund refund = next.get();
        try {
            String refundId = existing(refund).orElseGet(() -> razorpay.refund(refund.paymentId(), refund.amountPaise(), refund.id()));
            jdbc.sql("UPDATE refund SET status = 'SENT', refund_id = ? WHERE id = ?").params(refundId, refund.id()).update();
        } catch (RuntimeException failed) {
            log.warn("refund {} failed", refund.id());
            String reason = String.valueOf(failed.getMessage());
            jdbc.sql("""
                            UPDATE refund SET attempts = attempts + 1, error = ?,
                                   status = CASE WHEN attempts + 1 >= 4 THEN 'FAILED' ELSE 'PENDING' END,
                                   next_attempt_at = now() + make_interval(secs => 60 * power(4, attempts) * (0.5 + random() * 0.5))
                            WHERE id = ?
                            """)
                    .params(reason.substring(0, Math.min(300, reason.length())), refund.id())
                    .update();
        }
        return true;
    }

    private Optional<String> existing(Refund refund) {
        for (JsonNode done : razorpay.refunds(refund.paymentId())) {
            if (String.valueOf(refund.id()).equals(done.path("notes").path("refund_row").asText())) {
                return Optional.of(done.path("id").asText());
            }
        }
        return Optional.empty();
    }

    private Held held(long bookingId) {
        return jdbc.sql("""
                        SELECT b.pay_status, b.amount_paise, b.held_until, b.order_id, u.email
                        FROM booking b JOIN users u ON u.id = b.user_id WHERE b.id = ?
                        """)
                .param(bookingId)
                .query((rs, row) -> new Held(rs.getString("pay_status"), rs.getLong("amount_paise"),
                        rs.getTimestamp("held_until") == null ? null : rs.getTimestamp("held_until").toInstant(),
                        rs.getString("order_id"), rs.getString("email")))
                .single();
    }

    private record Held(String payStatus, long amountPaise, Instant heldUntil, String orderId, String email) {
    }

    private record Expired(long id, String orderId) {
    }

    private record Refund(long id, String paymentId, long amountPaise) {
    }
}

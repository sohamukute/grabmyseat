package com.grabmyseat.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm").withZone(ZoneId.of("Asia/Kolkata"));
    private static final String SEATS_SQL = """
            SELECT e.title, e.starts_at, v.name AS venue, v.city, a.name AS area,
                   s.row_label || s.number AS seat, t.attendee_name, t.code
            FROM ticket t
            JOIN booking b ON b.id = t.booking_id
            JOIN event e ON e.id = t.event_id
            JOIN venue v ON v.id = e.venue_id
            JOIN seat s ON s.id = t.seat_id
            JOIN area a ON a.id = s.area_id
            WHERE %s
            ORDER BY a.rank, s.row_rank, s.number
            """;

    private final JdbcClient jdbc;
    private final JavaMailSender mail;
    private final String from;
    private final String appUrl;

    public NotificationSender(JdbcClient jdbc, JavaMailSender mail, @Value("${app.mail-from}") String from,
                              @Value("${app.url}") String appUrl) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.from = from;
        this.appUrl = appUrl;
    }

    @Scheduled(fixedDelay = 600_000)
    public void queueReminders() {
        jdbc.sql("""
                        INSERT INTO notification (user_id, kind, ref_id)
                        SELECT DISTINCT b.user_id, 'REMINDER', e.id
                        FROM event e
                        JOIN ticket t ON t.event_id = e.id AND t.status = 'VALID'
                        JOIN booking b ON b.id = t.booking_id
                        WHERE e.status = 'PUBLISHED'
                        AND e.starts_at BETWEEN now() AND now() + interval '24 hours'
                        AND b.created_at < e.starts_at - interval '24 hours'
                        ON CONFLICT DO NOTHING
                        """)
                .update();
    }

    @Scheduled(fixedDelay = 86_400_000)
    public void cleanUp() {
        jdbc.sql("DELETE FROM notification WHERE status IN ('SENT', 'SKIPPED') AND created_at < now() - interval '30 days'")
                .update();
    }

    @Scheduled(fixedDelay = 5_000)
    public void send() {
        try {
            int sent = 0;
            while (sent < 50 && sendNext()) {
                sent++;
            }
            jdbc.sql("""
                            UPDATE notification SET status = 'FAILED', error = 'interrupted while sending'
                            WHERE status = 'SENDING' AND claimed_at < now() - interval '10 minutes'
                            """)
                    .update();
        } catch (RuntimeException stopped) {
            log.error("notification sender stopped", stopped);
        }
    }

    private boolean sendNext() {
        Optional<Pending> next = jdbc.sql("""
                        UPDATE notification n SET status = 'SENDING', claimed_at = now()
                        FROM users u
                        WHERE u.id = n.user_id AND n.id = (
                            SELECT id FROM notification
                            WHERE status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= now())
                            ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED)
                        RETURNING n.id, n.kind, n.ref_id, n.user_id, u.username, u.email
                        """)
                .query(Pending.class).optional();
        if (next.isEmpty()) {
            return false;
        }
        try {
            deliver(next.get());
        } catch (RuntimeException failed) {
            log.warn("notification {} failed", next.get().id());
            String reason = String.valueOf(failed.getMessage());
            jdbc.sql("""
                            UPDATE notification SET attempts = attempts + 1, error = ?,
                                   status = CASE WHEN attempts + 1 >= 4 THEN 'FAILED' ELSE 'PENDING' END,
                                   next_attempt_at = now() + make_interval(secs => 60 * power(4, attempts) * (0.5 + random() * 0.5))
                            WHERE id = ?
                            """)
                    .params(reason.substring(0, Math.min(300, reason.length())), next.get().id())
                    .update();
        }
        return true;
    }

    private void deliver(Pending pending) {
        Optional<Mail> message = compose(pending);
        if (pending.email() == null || message.isEmpty()) {
            finish(pending.id(), "SKIPPED", null);
            return;
        }
        SimpleMailMessage out = new SimpleMailMessage();
        out.setFrom(from);
        out.setTo(pending.email());
        out.setSubject(message.get().subject());
        out.setText(message.get().body());
        mail.send(out);
        finish(pending.id(), "SENT", null);
    }

    private void finish(long id, String status, String error) {
        jdbc.sql("UPDATE notification SET status = ?, error = ?, sent_at = CASE WHEN ? = 'SENT' THEN now() END WHERE id = ?")
                .params(status, error, status, id)
                .update();
    }

    private Optional<Mail> compose(Pending pending) {
        return switch (pending.kind()) {
            case "BOOKED" -> booked(pending, "You are booked, and you are sitting together.");
            case "SEATED" -> booked(pending, "Seats came back in the line you joined, and they are now booked for you.");
            case "REMINDER" -> reminder(pending);
            case "CANCELLED" -> cancelled(pending);
            case "PASSWORD" -> passwordChanged(pending);
            default -> Optional.empty();
        };
    }

    private Optional<Mail> booked(Pending pending, String opening) {
        boolean unpaid = jdbc.sql("SELECT pay_status = 'HELD' FROM booking WHERE id = ?").param(pending.refId())
                .query(Boolean.class).optional().orElse(false);
        List<Line> lines = jdbc.sql(SEATS_SQL.formatted("b.id = ? AND t.status IN ('HELD', 'VALID')")).param(pending.refId()).query(Line.class).list();
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        Line first = lines.get(0);
        if (unpaid) {
            return Optional.of(new Mail("Seats came back for you: " + first.title(),
                    "Hi " + pending.username() + ",\n\nSeats came back in the line you joined and they are held for you for 10 minutes.\n\n"
                            + details(lines, false) + "\nPay here to keep them: " + appUrl + "/#/tickets\n"
                            + "If you do not pay in time, they go to the next group in line.\n"));
        }
        return Optional.of(new Mail("Your seats for " + first.title(),
                "Hi " + pending.username() + ",\n\n" + opening + "\n\n" + details(lines, true)
                        + "\nShow these codes at the gate. Each person needs their own code.\n"
                        + "You can see them any time under My tickets.\n"));
    }

    private Optional<Mail> reminder(Pending pending) {
        List<Line> lines = jdbc.sql(SEATS_SQL.formatted("b.user_id = ? AND t.event_id = ? AND t.status = 'VALID'"))
                .params(pending.userId(), pending.refId())
                .query(Line.class).list();
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Mail("Coming up: " + lines.get(0).title(),
                "Hi " + pending.username() + ",\n\nYour show is less than a day away.\n\n" + details(lines, true)
                        + "\nCannot make it? Cancel under My tickets so people waiting in line get your seats.\n"));
    }

    private Optional<Mail> cancelled(Pending pending) {
        return jdbc.sql("SELECT title, starts_at FROM event WHERE id = ?")
                .param(pending.refId())
                .query((rs, row) -> new Mail(rs.getString("title") + " is cancelled",
                        "Hi " + pending.username() + ",\n\nThe organiser cancelled " + rs.getString("title") + " on "
                                + WHEN.format(rs.getTimestamp("starts_at").toInstant()) + ".\n"
                                + "Your tickets are cancelled and your place in any line is closed. There is nothing else you need to do.\n"))
                .optional();
    }

    private Optional<Mail> passwordChanged(Pending pending) {
        return jdbc.sql("SELECT used_at FROM password_reset WHERE id = ?")
                .param(pending.refId())
                .query((rs, row) -> new Mail("Your GrabMySeat password was changed",
                        "Hi " + pending.username() + ",\n\nThe password for your account was changed on "
                                + WHEN.format(rs.getTimestamp("used_at").toInstant()) + ". You have been signed out on every device.\n\n"
                                + "If this was not you, use Forgot password on the sign in page right away to take your account back.\n"))
                .optional();
    }

    private String details(List<Line> lines, boolean codes) {
        Line first = lines.get(0);
        StringBuilder text = new StringBuilder(first.title() + "\n" + WHEN.format(first.startsAt()) + " at "
                + first.venue() + ", " + first.city() + ". Doors open 30 minutes before.\n\n");
        lines.forEach(line -> text.append(String.format("  %-13s %-5s %-20s%s%n",
                line.area(), line.seat(), line.attendeeName(), codes ? " code " + line.code() : "")));
        return text.toString();
    }

    private record Pending(long id, String kind, long refId, long userId, String username, String email) {
    }

    private record Line(String title, Instant startsAt, String venue, String city, String area, String seat,
                        String attendeeName, String code) {
    }

    private record Mail(String subject, String body) {
    }
}

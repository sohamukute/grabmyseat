package com.grabmyseat.checkin;

import com.grabmyseat.auth.web.ApiException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class CheckInService {

    private final JdbcClient jdbc;

    public CheckInService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<StaffEvent> myEvents(long staffId) {
        return jdbc.sql("""
                        SELECT e.id, e.title, v.city, e.starts_at, e.ends_at FROM staff_assignment sa
                        JOIN event e ON e.id = sa.event_id
                        JOIN venue v ON v.id = e.venue_id
                        WHERE sa.user_id = ? AND e.status = 'PUBLISHED'
                        ORDER BY e.starts_at
                        """)
                .param(staffId)
                .query(StaffEvent.class).list();
    }

    @Transactional
    public CheckInResult checkIn(long staffId, CheckInRequest request) {
        boolean staff = jdbc.sql("SELECT count(*) FROM staff_assignment WHERE event_id = ? AND user_id = ?")
                .params(request.eventId(), staffId)
                .query(Long.class).single() > 0;
        if (!staff) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not on the staff list for this event.");
        }
        Window window = jdbc.sql("SELECT starts_at, ends_at FROM event WHERE id = ?")
                .param(request.eventId())
                .query(Window.class).optional()
                .orElseThrow(() -> new EntityNotFoundException("event not found"));
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(window.startsAt().minus(Duration.ofHours(2)))) {
            return refused("Check in opens 2 hours before the event starts.", null);
        }
        if (now.isAfter(window.endsAt())) {
            return refused("This event has ended. Check in is closed.", null);
        }

        String code = request.code().trim().toUpperCase(Locale.ROOT);
        Optional<Admitted> admitted = jdbc.sql("""
                        UPDATE ticket SET status = 'USED', checked_in_at = now(), checked_in_by = ?
                        WHERE code = ? AND event_id = ? AND status = 'VALID'
                        RETURNING attendee_name, (SELECT row_label || number FROM seat WHERE id = seat_id) AS seat
                        """)
                .params(staffId, code, request.eventId())
                .query(Admitted.class).optional();
        if (admitted.isPresent()) {
            return new CheckInResult(true, "Welcome in.", admitted.get().attendeeName(), admitted.get().seat(),
                    null, null);
        }

        Optional<TicketRow> found = jdbc.sql("""
                        SELECT t.event_id, t.status, t.attendee_name, s.row_label || s.number AS seat,
                               t.checked_in_at, u.username AS checked_in_by
                        FROM ticket t
                        JOIN seat s ON s.id = t.seat_id
                        LEFT JOIN users u ON u.id = t.checked_in_by
                        WHERE t.code = ?
                        """)
                .param(code)
                .query(TicketRow.class).optional();
        if (found.isEmpty()) {
            return refused("Unknown code. Check it and try again.", null);
        }
        TicketRow ticket = found.get();
        if (ticket.eventId() != request.eventId()) {
            return refused("This ticket is for another event.", null);
        }
        if (ticket.status().equals("CANCELLED")) {
            return refused("This ticket was cancelled.", ticket);
        }
        return new CheckInResult(false, "Already used.",
                ticket.attendeeName(), ticket.seat(), ticket.checkedInAt(), ticket.checkedInBy());
    }

    private CheckInResult refused(String message, TicketRow ticket) {
        return ticket == null
                ? new CheckInResult(false, message, null, null, null, null)
                : new CheckInResult(false, message, ticket.attendeeName(), ticket.seat(), null, null);
    }

    public record StaffEvent(long id, String title, String city, OffsetDateTime startsAt, OffsetDateTime endsAt) {
    }

    private record Window(OffsetDateTime startsAt, OffsetDateTime endsAt) {
    }

    private record Admitted(String attendeeName, String seat) {
    }

    private record TicketRow(long eventId, String status, String attendeeName, String seat,
                             OffsetDateTime checkedInAt, String checkedInBy) {
    }
}

package com.grabmyseat.waiting;

import com.grabmyseat.auth.web.ApiException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WaitingRoom {

    private static final Logger log = LoggerFactory.getLogger(WaitingRoom.class);
    private static final int SHOPPERS = 200;
    private static final Duration EARLY = Duration.ofMinutes(15);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm 'IST'").withZone(ZoneId.of("Asia/Kolkata"));

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Map<Long, Boolean> soldOut = new ConcurrentHashMap<>();

    public WaitingRoom(JdbcClient jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
    }

    public WaitingStatus join(long userId, long eventId) {
        Room room = room(eventId);
        boolean verified = jdbc.sql("SELECT email_verified_at IS NOT NULL FROM users WHERE id = ?").param(userId).query(Boolean.class).single();
        if (!verified) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Confirm your email first, then join the waiting room.");
        }
        Instant now = Instant.now();
        if (now.isBefore(room.opensAt().minus(EARLY))) {
            throw new ApiException(HttpStatus.CONFLICT, "The waiting room opens " + TIME.format(room.opensAt().minus(EARLY)) + ". Come back then.");
        }
        if (!now.isBefore(room.closesAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "Booking for this event has closed.");
        }
        jdbc.sql("""
                        INSERT INTO waiting_room (event_id, user_id) VALUES (?, ?)
                        ON CONFLICT (event_id, user_id) DO UPDATE
                        SET joined_at = now(), position = NULL, admitted_at = NULL, pass_expires_at = NULL
                        WHERE waiting_room.pass_expires_at <= now() AND waiting_room.done_at IS NULL
                        """)
                .params(eventId, userId)
                .update();
        return status(userId, eventId);
    }

    public WaitingStatus status(long userId, long eventId) {
        Room room = room(eventId);
        Instant roomOpens = room.opensAt().minus(EARLY);
        return jdbc.sql("""
                        SELECT w.position, w.pass_expires_at,
                               (SELECT count(*) FROM waiting_room o WHERE o.event_id = w.event_id
                                AND o.admitted_at IS NULL AND o.position < w.position) AS ahead
                        FROM waiting_room w WHERE w.event_id = ? AND w.user_id = ?
                        """)
                .params(eventId, userId)
                .query((rs, row) -> {
                    Integer position = (Integer) rs.getObject("position");
                    Instant pass = rs.getTimestamp("pass_expires_at") == null ? null : rs.getTimestamp("pass_expires_at").toInstant();
                    String state = pass == null
                            ? soldOut.getOrDefault(eventId, false) ? "SOLD_OUT" : "WAITING"
                            : pass.isAfter(Instant.now()) ? "TURN" : "EXPIRED";
                    return new WaitingStatus(state, position, rs.getLong("ahead"), pass, roomOpens, room.opensAt());
                })
                .optional()
                .orElse(new WaitingStatus("OUT", null, 0, null, roomOpens, room.opensAt()));
    }

    @Scheduled(fixedDelay = 2_000)
    public void admit() {
        List<Long> events = jdbc.sql("""
                        SELECT e.id FROM event e
                        WHERE e.waiting_room AND e.status = 'PUBLISHED'
                        AND e.booking_opens_at <= now() AND e.booking_closes_at > now()
                        AND EXISTS (SELECT 1 FROM waiting_room w WHERE w.event_id = e.id AND w.admitted_at IS NULL)
                        """)
                .query(Long.class).list();
        for (long eventId : events) {
            soldOut.put(eventId, jdbc.sql("""
                            SELECT NOT EXISTS (
                                SELECT 1 FROM event_area ea JOIN seat s ON s.area_id = ea.area_id
                                WHERE ea.event_id = :event AND NOT EXISTS (
                                    SELECT 1 FROM ticket t WHERE t.event_id = :event AND t.seat_id = s.id
                                    AND t.status IN ('HELD', 'VALID', 'USED')))
                            """)
                    .param("event", eventId)
                    .query(Boolean.class).single());
            try {
                tx.executeWithoutResult(status -> admit(eventId));
            } catch (RuntimeException failed) {
                log.warn("waiting room {} admit failed", eventId);
            }
        }
    }

    private void admit(long eventId) {
        boolean mine = jdbc.sql("SELECT pg_try_advisory_xact_lock(7001, ?::int)").param(eventId).query(Boolean.class).single();
        if (!mine) {
            return;
        }
        boolean drawn = jdbc.sql("SELECT EXISTS (SELECT 1 FROM waiting_room WHERE event_id = ? AND position IS NOT NULL)")
                .param(eventId).query(Boolean.class).single();
        jdbc.sql(drawn ? """
                        UPDATE waiting_room w
                        SET position = r.n + (SELECT coalesce(max(position), 0) FROM waiting_room WHERE event_id = :event)
                        FROM (SELECT user_id, row_number() OVER (ORDER BY joined_at, user_id) AS n
                              FROM waiting_room WHERE event_id = :event AND position IS NULL) r
                        WHERE w.event_id = :event AND w.user_id = r.user_id
                        """ : """
                        UPDATE waiting_room w SET position = r.n
                        FROM (SELECT user_id, row_number() OVER (ORDER BY random()) AS n
                              FROM waiting_room WHERE event_id = :event) r
                        WHERE w.event_id = :event AND w.user_id = r.user_id
                        """)
                .param("event", eventId)
                .update();
        long shopping = jdbc.sql("""
                        SELECT count(*) FROM waiting_room
                        WHERE event_id = ? AND pass_expires_at > now() AND done_at IS NULL
                        """)
                .param(eventId).query(Long.class).single();
        if (shopping >= SHOPPERS) {
            return;
        }
        jdbc.sql("""
                        UPDATE waiting_room SET admitted_at = now(), pass_expires_at = now() + interval '10 minutes'
                        WHERE event_id = :event AND user_id IN (
                            SELECT user_id FROM waiting_room
                            WHERE event_id = :event AND admitted_at IS NULL AND position IS NOT NULL
                            ORDER BY position LIMIT :room)
                        """)
                .param("event", eventId)
                .param("room", SHOPPERS - shopping)
                .update();
    }

    private Room room(long eventId) {
        Optional<Room> room = jdbc.sql("""
                        SELECT booking_opens_at, booking_closes_at FROM event
                        WHERE id = ? AND waiting_room AND status = 'PUBLISHED'
                        """)
                .param(eventId)
                .query((rs, row) -> new Room(rs.getTimestamp("booking_opens_at").toInstant(), rs.getTimestamp("booking_closes_at").toInstant()))
                .optional();
        return room.orElseThrow(() -> new EntityNotFoundException("no waiting room"));
    }

    private record Room(Instant opensAt, Instant closesAt) {
    }
}

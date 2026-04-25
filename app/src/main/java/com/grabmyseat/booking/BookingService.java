package com.grabmyseat.booking;

import com.grabmyseat.auth.web.ApiException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private final JdbcClient jdbc;
    private static final DateTimeFormatter OPENS = DateTimeFormatter.ofPattern("d MMM, HH:mm 'IST'").withZone(ZoneId.of("Asia/Kolkata"));
    private static final String CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom random = new SecureRandom();
    private final SeatAllocator allocator = new SeatAllocator();

    public BookingService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public BookingResponse book(long userId, BookingRequest request) {
        checkNames(request.attendeeNames(), request.groupSize());
        EventRow event = lockEvent(request.eventId());
        checkVerified(lockUser(userId));
        Optional<Long> repeat = findBooking(userId, request.requestId());
        if (repeat.isPresent()) {
            return load(repeat.get());
        }
        checkOpen(event);
        checkTurn(userId, request.eventId(), event);
        checkLimit(userId, request.eventId(), event, request.groupSize());
        if (freeSeats(request.eventId(), request.areaId()) < request.groupSize()) {
            throw new ApiException(HttpStatus.CONFLICT, "Not enough seats together in this area. Allow split seating or join the queue.");
        }
        lockArea(request.eventId(), request.areaId());

        AreaGrid grid = grid(request.eventId(), request.areaId());
        List<Seat> picked = request.seatIds() == null || request.seatIds().isEmpty()
                ? allocator.allocate(grid, request.groupSize(), request.splitOk())
                        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                                "Not enough seats together in this area. Allow split seating or join the queue."))
                : chosen(grid, request);
        BookingResponse booked = insertBooking(userId, request.eventId(), request.requestId(), picked, request.attendeeNames(), "BOOKED");
        doneShopping(userId, request.eventId(), event);
        return booked;
    }

    @Transactional
    public JoinResult joinQueue(long userId, JoinQueueRequest request) {
        checkNames(request.attendeeNames(), request.groupSize());
        EventRow event = lockEvent(request.eventId());
        checkVerified(lockUser(userId));
        Optional<Long> bookedBefore = findBooking(userId, request.requestId());
        if (bookedBefore.isPresent()) {
            return new JoinResult(load(bookedBefore.get()), null);
        }
        Optional<Long> joinedBefore = jdbc.sql("SELECT id FROM queue_request WHERE user_id = ? AND request_id = ?")
                .params(userId, request.requestId())
                .query(Long.class).optional();
        if (joinedBefore.isPresent()) {
            return new JoinResult(null, queue(userId, joinedBefore.get()));
        }
        checkOpen(event);
        checkTurn(userId, request.eventId(), event);
        doneShopping(userId, request.eventId(), event);
        boolean waiting = jdbc.sql("SELECT count(*) FROM queue_request WHERE user_id = ? AND event_id = ? AND status = 'WAITING'")
                .params(userId, request.eventId())
                .query(Long.class).single() > 0;
        if (waiting) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "You are already in a queue for this event. Leave it first to join another.");
        }
        checkLimit(userId, request.eventId(), event, request.groupSize());
        lockArea(request.eventId(), request.areaId());

        Optional<List<Seat>> fits = allocator.allocate(grid(request.eventId(), request.areaId()),
                request.groupSize(), request.splitOk());
        if (fits.isPresent()) {
            return new JoinResult(insertBooking(userId, request.eventId(), request.requestId(), fits.get(),
                    request.attendeeNames(), "BOOKED"), null);
        }

        long id = jdbc.sql("""
                        INSERT INTO queue_request (event_id, area_id, user_id, group_size, split_ok, request_id,
                                                   status, attendee_names)
                        VALUES (?, ?, ?, ?, ?, ?, 'WAITING', ?) RETURNING id
                        """)
                .params(request.eventId(), request.areaId(), userId, request.groupSize(), request.splitOk(),
                        request.requestId(), request.attendeeNames().stream().map(String::trim).toArray(String[]::new))
                .query(Long.class).single();
        return new JoinResult(null, queue(userId, id));
    }

    @Transactional(readOnly = true)
    public QueueResponse queue(long userId, long id) {
        return jdbc.sql("""
                        SELECT q.id, q.event_id, q.area_id, q.group_size, q.split_ok, q.status, q.booking_id,
                               CASE WHEN q.status = 'WAITING' THEN
                                   (SELECT count(*) FROM queue_request o
                                    WHERE o.event_id = q.event_id AND o.area_id = q.area_id AND o.status = 'WAITING'
                                    AND (o.joined_at, o.id) <= (q.joined_at, q.id))
                               ELSE 0 END AS position
                        FROM queue_request q WHERE q.id = ? AND q.user_id = ?
                        """)
                .params(id, userId)
                .query(QueueResponse.class).optional()
                .orElseThrow(() -> new EntityNotFoundException("queue request not found"));
    }

    @Transactional
    public QueueResponse leaveQueue(long userId, long id) {
        QueueResponse current = queue(userId, id);
        int left = jdbc.sql("UPDATE queue_request SET status = 'LEFT' WHERE id = ? AND user_id = ? AND status = 'WAITING'")
                .params(id, userId)
                .update();
        if (!current.status().canMoveTo(QueueStatus.LEFT) || left == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "You are no longer waiting in this queue.");
        }
        return queue(userId, id);
    }

    @Transactional(readOnly = true)
    public Optional<MyTickets> myTickets(long userId, String known) {
        String versions = jdbc.sql("""
                        SELECT coalesce(string_agg(ea.event_id || ':' || ea.area_id || ':' || ea.version, ','
                                                   ORDER BY ea.event_id, ea.area_id), '')
                        FROM event_area ea
                        WHERE (ea.event_id, ea.area_id) IN (
                            SELECT t.event_id, s.area_id FROM ticket t
                            JOIN booking b ON b.id = t.booking_id
                            JOIN seat s ON s.id = t.seat_id
                            WHERE b.user_id = :user
                            UNION
                            SELECT event_id, area_id FROM queue_request WHERE user_id = :user)
                        """)
                .param("user", userId)
                .query(String.class).single();
        if (versions.equals(known)) {
            return Optional.empty();
        }
        List<MyTicket> tickets = jdbc.sql("""
                        SELECT t.id AS ticket_id, t.booking_id, t.code, e.id AS event_id,
                               e.title AS event_title, e.starts_at, a.name AS area_name, s.row_label, s.number,
                               t.attendee_name, t.status
                        FROM ticket t
                        JOIN booking b ON b.id = t.booking_id
                        JOIN event e ON e.id = t.event_id
                        JOIN seat s ON s.id = t.seat_id
                        JOIN area a ON a.id = s.area_id
                        WHERE b.user_id = ?
                        ORDER BY e.starts_at, t.id
                        """)
                .param(userId)
                .query(MyTicket.class).list();
        return Optional.of(new MyTickets(versions, tickets));
    }

    @Transactional(readOnly = true)
    public BookingResponse booking(long userId, long bookingId) {
        jdbc.sql("SELECT id FROM booking WHERE id = ? AND user_id = ?")
                .params(bookingId, userId)
                .query(Long.class).optional()
                .orElseThrow(() -> new EntityNotFoundException("booking not found"));
        return load(bookingId);
    }

    @Transactional
    public void cancelTicket(long userId, long ticketId) {
        Cancelled cancelled = jdbc.sql("""
                        SELECT t.event_id, s.area_id, e.starts_at FROM ticket t
                        JOIN booking b ON b.id = t.booking_id
                        JOIN seat s ON s.id = t.seat_id
                        JOIN event e ON e.id = t.event_id
                        WHERE t.id = ? AND b.user_id = ?
                        """)
                .params(ticketId, userId)
                .query(Cancelled.class).optional()
                .orElseThrow(() -> new EntityNotFoundException("ticket not found"));
        checkNotStarted(cancelled);
        lockForCancel(userId, cancelled);
        boolean unpaid = jdbc.sql("SELECT status = 'HELD' FROM ticket WHERE id = ?").param(ticketId).query(Boolean.class).single();
        if (unpaid) {
            throw new ApiException(HttpStatus.CONFLICT, "This booking is waiting for payment. Cancel the whole booking instead.");
        }
        finishCancel(cancelTickets("t.id = ?", ticketId), cancelled);
    }

    @Transactional
    public void cancelBooking(long userId, long bookingId) {
        Cancelled cancelled = jdbc.sql("""
                        SELECT b.event_id, s.area_id, e.starts_at FROM booking b
                        JOIN ticket t ON t.booking_id = b.id
                        JOIN seat s ON s.id = t.seat_id
                        JOIN event e ON e.id = b.event_id
                        WHERE b.id = ? AND b.user_id = ?
                        LIMIT 1
                        """)
                .params(bookingId, userId)
                .query(Cancelled.class).optional()
                .orElseThrow(() -> new EntityNotFoundException("booking not found"));
        checkNotStarted(cancelled);
        lockForCancel(userId, cancelled);
        finishCancel(cancelTickets("t.booking_id = ?", bookingId), cancelled);
    }

    @Transactional
    public void cancelEventTickets(long eventId) {
        jdbc.sql("""
                        INSERT INTO notification (user_id, kind, ref_id)
                        SELECT b.user_id, 'CANCELLED', :event FROM ticket t JOIN booking b ON b.id = t.booking_id
                        WHERE t.event_id = :event AND t.status IN ('HELD', 'VALID')
                        UNION
                        SELECT user_id, 'CANCELLED', :event FROM queue_request WHERE event_id = :event AND status = 'WAITING'
                        ON CONFLICT DO NOTHING
                        """)
                .param("event", eventId)
                .update();
        jdbc.sql("""
                        INSERT INTO refund (booking_id, payment_id, amount_paise, reason)
                        SELECT b.id, b.payment_id, sum(t.price_paise), 'event'
                        FROM booking b JOIN ticket t ON t.booking_id = b.id
                        WHERE b.event_id = ? AND b.pay_status = 'PAID' AND t.status = 'VALID'
                        GROUP BY b.id, b.payment_id
                        """)
                .param(eventId)
                .update();
        jdbc.sql("UPDATE ticket SET status = 'CANCELLED' WHERE event_id = ? AND status IN ('HELD', 'VALID')")
                .param(eventId)
                .update();
        jdbc.sql("UPDATE booking SET pay_status = 'RELEASED' WHERE event_id = ? AND pay_status = 'HELD'")
                .param(eventId)
                .update();
        jdbc.sql("UPDATE queue_request SET status = 'CLOSED' WHERE event_id = ? AND status = 'WAITING'")
                .param(eventId)
                .update();
        jdbc.sql("UPDATE event_area SET version = version + 1 WHERE event_id = ?")
                .param(eventId)
                .update();
    }

    @Transactional
    public String markPaid(long bookingId, String paymentId) {
        Owner owner = owner(bookingId);
        lockEvent(owner.eventId());
        lockUser(owner.userId());
        lockArea(owner.eventId(), owner.areaId());
        Pay pay = jdbc.sql("SELECT pay_status, payment_id, amount_paise FROM booking WHERE id = ? FOR UPDATE")
                .param(bookingId)
                .query(Pay.class).single();
        if ("PAID".equals(pay.payStatus())) {
            if (!paymentId.equals(pay.paymentId())) {
                refund(bookingId, paymentId, pay.amountPaise(), "duplicate");
            }
            return "PAID";
        }
        if ("RELEASED".equals(pay.payStatus())) {
            jdbc.sql("UPDATE booking SET payment_id = ? WHERE id = ? AND payment_id IS NULL").params(paymentId, bookingId).update();
            refund(bookingId, paymentId, pay.amountPaise(), "late");
            return "LATE";
        }
        jdbc.sql("UPDATE ticket SET status = 'VALID' WHERE booking_id = ? AND status = 'HELD'").param(bookingId).update();
        jdbc.sql("UPDATE booking SET pay_status = 'PAID', payment_id = ?, paid_at = now() WHERE id = ?").params(paymentId, bookingId).update();
        jdbc.sql("UPDATE event_area SET version = version + 1 WHERE event_id = ? AND area_id = ?").params(owner.eventId(), owner.areaId()).update();
        jdbc.sql("INSERT INTO notification (user_id, kind, ref_id) VALUES (?, 'BOOKED', ?) ON CONFLICT DO NOTHING")
                .params(owner.userId(), bookingId)
                .update();
        return "PAID";
    }

    @Transactional
    public void release(long bookingId) {
        Owner owner = owner(bookingId);
        lockEvent(owner.eventId());
        lockUser(owner.userId());
        lockArea(owner.eventId(), owner.areaId());
        int released = jdbc.sql("UPDATE booking SET pay_status = 'RELEASED' WHERE id = ? AND pay_status = 'HELD' AND held_until < now()")
                .param(bookingId)
                .update();
        if (released == 0) {
            return;
        }
        jdbc.sql("UPDATE ticket SET status = 'CANCELLED' WHERE booking_id = ? AND status = 'HELD'").param(bookingId).update();
        jdbc.sql("UPDATE event_area SET version = version + 1 WHERE event_id = ? AND area_id = ?").params(owner.eventId(), owner.areaId()).update();
        matchQueue(owner.eventId(), owner.areaId());
    }

    @Transactional(readOnly = true)
    public long bookingOf(long userId, long bookingId) {
        return jdbc.sql("SELECT id FROM booking WHERE id = ? AND user_id = ?")
                .params(bookingId, userId)
                .query(Long.class).optional()
                .orElseThrow(() -> new EntityNotFoundException("booking not found"));
    }

    private int cancelTickets(String where, long id) {
        List<Gone> gone = jdbc.sql("""
                        UPDATE ticket t SET status = 'CANCELLED' FROM ticket old
                        WHERE old.id = t.id AND %s AND t.status IN ('HELD', 'VALID')
                        RETURNING old.status AS was, t.price_paise, t.booking_id
                        """.formatted(where))
                .param(id)
                .query(Gone.class).list();
        gone.stream().map(Gone::bookingId).distinct().forEach(bookingId -> {
            long paid = gone.stream().filter(g -> g.bookingId() == bookingId && g.was().equals("VALID")).mapToLong(Gone::pricePaise).sum();
            if (paid > 0) {
                jdbc.sql("""
                                INSERT INTO refund (booking_id, payment_id, amount_paise, reason)
                                SELECT id, payment_id, ?, 'cancel' FROM booking WHERE id = ? AND pay_status = 'PAID'
                                """)
                        .params(paid, bookingId)
                        .update();
            }
            jdbc.sql("UPDATE booking SET pay_status = 'RELEASED' WHERE id = ? AND pay_status = 'HELD'").param(bookingId).update();
        });
        return gone.size();
    }

    private void refund(long bookingId, String paymentId, long amountPaise, String reason) {
        jdbc.sql("INSERT INTO refund (booking_id, payment_id, amount_paise, reason) VALUES (?, ?, ?, ?)")
                .params(bookingId, paymentId, amountPaise, reason)
                .update();
    }

    private Owner owner(long bookingId) {
        return jdbc.sql("""
                        SELECT b.event_id, b.user_id, s.area_id FROM booking b
                        JOIN ticket t ON t.booking_id = b.id JOIN seat s ON s.id = t.seat_id
                        WHERE b.id = ? LIMIT 1
                        """)
                .param(bookingId)
                .query(Owner.class).optional()
                .orElseThrow(() -> new EntityNotFoundException("booking not found"));
    }

    private void bumpVersion(long eventId, long seatId) {
        jdbc.sql("""
                        UPDATE event_area SET version = version + 1
                        WHERE event_id = ? AND area_id = (SELECT area_id FROM seat WHERE id = ?)
                        """)
                .params(eventId, seatId)
                .update();
    }

    private void lockForCancel(long userId, Cancelled cancelled) {
        lockEvent(cancelled.eventId());
        lockUser(userId);
        lockArea(cancelled.eventId(), cancelled.areaId());
    }

    private void finishCancel(int rows, Cancelled cancelled) {
        if (rows == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Only valid tickets can be cancelled. This one is used or already cancelled.");
        }
        jdbc.sql("UPDATE event_area SET version = version + 1 WHERE event_id = ? AND area_id = ?")
                .params(cancelled.eventId(), cancelled.areaId())
                .update();
        matchQueue(cancelled.eventId(), cancelled.areaId());
    }

    private void matchQueue(long eventId, long areaId) {
        List<Waiting> waiting = jdbc.sql("""
                        SELECT id, user_id, group_size, split_ok, request_id, attendee_names FROM queue_request
                        WHERE event_id = ? AND area_id = ? AND status = 'WAITING'
                        ORDER BY joined_at, id
                        """)
                .params(eventId, areaId)
                .query((rs, row) -> new Waiting(rs.getLong("id"), rs.getLong("user_id"), rs.getInt("group_size"),
                        rs.getBoolean("split_ok"), rs.getObject("request_id", UUID.class),
                        List.of((String[]) rs.getArray("attendee_names").getArray())))
                .list();
        AreaGrid grid = grid(eventId, areaId);
        for (Waiting request : waiting) {
            Optional<List<Seat>> fits = allocator.allocate(grid, request.groupSize(), request.splitOk());
            if (fits.isEmpty()) {
                continue;
            }
            BookingResponse booking = insertBooking(request.userId(), eventId, request.requestId(), fits.get(),
                    request.attendeeNames(), "SEATED");
            jdbc.sql("UPDATE queue_request SET status = 'SEATED', booking_id = ? WHERE id = ? AND status = 'WAITING'")
                    .params(booking.bookingId(), request.id())
                    .update();
            fits.get().forEach(seat -> grid.taken().add(seat.id()));
        }
    }

    private void checkNotStarted(Cancelled cancelled) {
        if (!OffsetDateTime.now().isBefore(cancelled.startsAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "This event has started. Tickets can no longer be cancelled.");
        }
    }

    private EventRow lockEvent(long eventId) {
        return jdbc.sql("""
                        SELECT status, booking_opens_at, booking_closes_at, max_per_person, waiting_room
                        FROM event WHERE id = ? FOR SHARE
                        """)
                .param(eventId)
                .query(EventRow.class).optional()
                .orElseThrow(() -> new EntityNotFoundException("event not found"));
    }

    private boolean lockUser(long userId) {
        return jdbc.sql("SELECT email_verified_at IS NOT NULL FROM users WHERE id = ? FOR UPDATE").param(userId).query(Boolean.class).single();
    }

    private void checkVerified(boolean verified) {
        if (!verified) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Confirm your email first. Use the link we sent you, or send a new one from the banner at the top.");
        }
    }

    private void lockArea(long eventId, long areaId) {
        jdbc.sql("SELECT version FROM event_area WHERE event_id = ? AND area_id = ? FOR UPDATE")
                .params(eventId, areaId)
                .query(Long.class).optional()
                .orElseThrow(() -> new EntityNotFoundException("area not found"));
    }

    private List<Seat> chosen(AreaGrid grid, BookingRequest request) {
        List<Long> ids = request.seatIds().stream().distinct().toList();
        if (ids.size() != request.groupSize()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Pick one seat for each person.");
        }
        Map<Long, Seat> byId = grid.seats().stream().collect(Collectors.toMap(Seat::id, seat -> seat));
        if (!byId.keySet().containsAll(ids)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Those seats are not in this area. Pick again.");
        }
        List<Seat> seats = ids.stream().map(byId::get).toList();
        List<String> gone = seats.stream().filter(seat -> !grid.isFree(seat)).map(seat -> seat.rowLabel() + seat.number()).toList();
        if (!gone.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, String.join(", ", gone) + (gone.size() == 1 ? " was" : " were")
                    + " just taken. Pick other seats.");
        }
        long freeAfter = grid.seats().size() - grid.taken().size() - seats.size();
        if (freeAfter * 10 > grid.seats().size() && allocator.leavesLoneSeat(grid, seats)) {
            throw new ApiException(HttpStatus.CONFLICT, "That choice leaves one empty seat on its own. Move your group by one seat.");
        }
        return seats;
    }

    private long freeSeats(long eventId, long areaId) {
        return jdbc.sql("""
                        SELECT count(*) FROM seat s WHERE s.area_id = :area AND NOT EXISTS (
                            SELECT 1 FROM ticket t WHERE t.event_id = :event AND t.seat_id = s.id AND t.status IN ('HELD', 'VALID', 'USED'))
                        """)
                .param("area", areaId)
                .param("event", eventId)
                .query(Long.class).single();
    }

        private Optional<Long> findBooking(long userId, UUID requestId) {
        return jdbc.sql("SELECT id FROM booking WHERE user_id = ? AND request_id = ?")
                .params(userId, requestId)
                .query(Long.class).optional();
    }

    private void checkNames(List<String> names, int groupSize) {
        if (names.size() != groupSize) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Give one attendee name for each seat.");
        }
    }

    private void checkLimit(long userId, long eventId, EventRow event, int groupSize) {
        long held = jdbc.sql("""
                        SELECT (SELECT count(*) FROM ticket t JOIN booking b ON b.id = t.booking_id
                                WHERE b.user_id = :user AND t.event_id = :event AND t.status IN ('HELD', 'VALID', 'USED'))
                             + (SELECT coalesce(sum(group_size), 0) FROM queue_request
                                WHERE user_id = :user AND event_id = :event AND status = 'WAITING')
                        """)
                .param("user", userId)
                .param("event", eventId)
                .query(Long.class).single();
        if (held + groupSize > event.maxPerPerson()) {
            throw new ApiException(HttpStatus.CONFLICT, "You can hold " + event.maxPerPerson()
                    + " seats for this event and already have " + held + ". Ask for fewer seats.");
        }
    }

    private AreaGrid grid(long eventId, long areaId) {
        List<SeatRow> rows = jdbc.sql("""
                        SELECT s.id, s.row_label, s.row_rank, s.number,
                               EXISTS (SELECT 1 FROM ticket t WHERE t.event_id = :event AND t.seat_id = s.id
                                       AND t.status IN ('HELD', 'VALID', 'USED')) AS taken
                        FROM seat s WHERE s.area_id = :area
                        """)
                .param("event", eventId)
                .param("area", areaId)
                .query(SeatRow.class).list();
        return new AreaGrid(rows.stream().map(row -> new Seat(row.id(), row.rowLabel(), row.rowRank(), row.number())).toList(),
                rows.stream().filter(SeatRow::taken).map(SeatRow::id).collect(Collectors.toSet()));
    }

    private BookingResponse insertBooking(long userId, long eventId, UUID requestId, List<Seat> picked,
                                          List<String> names, String notice) {
        bumpVersion(eventId, picked.get(0).id());
        long price = jdbc.sql("SELECT ea.price_paise FROM event_area ea JOIN seat s ON s.area_id = ea.area_id WHERE ea.event_id = ? AND s.id = ?")
                .params(eventId, picked.get(0).id())
                .query(Long.class).single();
        boolean held = price > 0;
        long bookingId = jdbc.sql("""
                        INSERT INTO booking (event_id, user_id, request_id, amount_paise, pay_status, held_until)
                        VALUES (?, ?, ?, ?, ?, CASE WHEN ? THEN now() + interval '10 minutes' END) RETURNING id
                        """)
                .params(eventId, userId, requestId, price * picked.size(), held ? "HELD" : null, held)
                .query(Long.class).single();
        Map<Long, String> nameBySeat = new HashMap<>();
        for (int i = 0; i < picked.size(); i++) {
            nameBySeat.put(picked.get(i).id(), names.get(i).trim());
        }
        Map<Long, Long> ticketBySeat = new HashMap<>();
        while (ticketBySeat.size() < picked.size()) {
            List<Seat> left = picked.stream().filter(seat -> !ticketBySeat.containsKey(seat.id())).toList();
            jdbc.sql("""
                            INSERT INTO ticket (booking_id, event_id, seat_id, attendee_name, status, code, price_paise)
                            SELECT ?, ?, seat, name, ?, code, ? FROM unnest(?::bigint[], ?::text[], ?::text[]) AS x (seat, name, code)
                            ON CONFLICT (code) DO NOTHING RETURNING id, seat_id
                            """)
                    .params(bookingId, eventId, held ? "HELD" : "VALID", price, left.stream().map(Seat::id).toArray(Long[]::new),
                            left.stream().map(seat -> nameBySeat.get(seat.id())).toArray(String[]::new),
                            left.stream().map(seat -> newCode()).toArray(String[]::new))
                    .query((rs, row) -> ticketBySeat.put(rs.getLong("seat_id"), rs.getLong("id")))
                    .list();
        }
        List<BookingResponse.BookedSeat> booked = picked.stream()
                .map(seat -> new BookingResponse.BookedSeat(ticketBySeat.get(seat.id()), seat.rowLabel(), seat.number(), nameBySeat.get(seat.id())))
                .toList();
        if (!held || notice.equals("SEATED")) {
            jdbc.sql("INSERT INTO notification (user_id, kind, ref_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING")
                    .params(userId, notice, bookingId)
                    .update();
        }
        return load(bookingId);
    }

    private String newCode() {
        StringBuilder code = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            code.append(CODE_LETTERS.charAt(random.nextInt(CODE_LETTERS.length())));
        }
        return code.toString();
    }

    private BookingResponse load(long bookingId) {
        List<BookingResponse.BookedSeat> seats = jdbc.sql("""
                        SELECT t.id AS ticket_id, s.row_label, s.number, t.attendee_name
                        FROM ticket t JOIN seat s ON s.id = t.seat_id
                        WHERE t.booking_id = ? ORDER BY t.id
                        """)
                .param(bookingId)
                .query(BookingResponse.BookedSeat.class).list();
        return jdbc.sql("SELECT amount_paise, pay_status, held_until FROM booking WHERE id = ?")
                .param(bookingId)
                .query((rs, row) -> new BookingResponse(bookingId, seats, rs.getLong("amount_paise"), rs.getString("pay_status"),
                        rs.getTimestamp("held_until") == null ? null : rs.getTimestamp("held_until").toInstant()))
                .single();
    }

    private void checkTurn(long userId, long eventId, EventRow event) {
        if (!event.waitingRoom()) {
            return;
        }
        boolean turn = jdbc.sql("SELECT count(*) FROM waiting_room WHERE event_id = ? AND user_id = ? AND pass_expires_at > now()")
                .params(eventId, userId)
                .query(Long.class).single() > 0;
        if (!turn) {
            throw new ApiException(HttpStatus.CONFLICT, "This show sells through the waiting room. Join it and book when it is your turn.");
        }
    }

    private void doneShopping(long userId, long eventId, EventRow event) {
        if (event.waitingRoom()) {
            jdbc.sql("UPDATE waiting_room SET done_at = now() WHERE event_id = ? AND user_id = ? AND done_at IS NULL")
                    .params(eventId, userId)
                    .update();
        }
    }

    private void checkOpen(EventRow event) {
        if (event.status().equals("CANCELLED")) {
            throw new ApiException(HttpStatus.CONFLICT, "This event was cancelled.");
        }
        if (!event.status().equals("PUBLISHED")) {
            throw new EntityNotFoundException("event not found");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(event.bookingOpensAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "Booking opens " + OPENS.format(event.bookingOpensAt()) + ". Come back then.");
        }
        if (!now.isBefore(event.bookingClosesAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "Booking for this event has closed.");
        }
    }

    private record Owner(long eventId, long userId, long areaId) {
    }

    private record Pay(String payStatus, String paymentId, long amountPaise) {
    }

    private record Gone(String was, long pricePaise, long bookingId) {
    }

    private record SeatRow(long id, String rowLabel, int rowRank, int number, boolean taken) {
    }

    private record Cancelled(long eventId, long areaId, OffsetDateTime startsAt) {
    }

    private record Waiting(long id, long userId, int groupSize, boolean splitOk, UUID requestId,
                           List<String> attendeeNames) {
    }

    private record EventRow(String status, OffsetDateTime bookingOpensAt, OffsetDateTime bookingClosesAt,
                            int maxPerPerson, boolean waitingRoom) {
    }
}

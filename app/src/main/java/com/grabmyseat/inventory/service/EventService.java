package com.grabmyseat.inventory.service;

import com.grabmyseat.auth.web.ApiException;
import com.grabmyseat.booking.BookingService;
import com.grabmyseat.inventory.dto.CreateEventRequest;
import com.grabmyseat.inventory.dto.EventReport;
import com.grabmyseat.inventory.dto.EventResponse;
import com.grabmyseat.inventory.dto.Performer;
import com.grabmyseat.inventory.dto.SeatDot;
import com.grabmyseat.inventory.dto.AreaCount;
import com.grabmyseat.inventory.dto.VenueResponse;
import com.grabmyseat.inventory.model.Event;
import com.grabmyseat.inventory.model.EventStatus;
import com.grabmyseat.inventory.model.Venue;
import com.grabmyseat.inventory.repository.EventRepository;
import com.grabmyseat.inventory.repository.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EventService {

    private final EventRepository events;
    private final VenueRepository venues;
    private final JdbcClient jdbc;
    private final BookingService bookingService;

    public EventService(EventRepository events, VenueRepository venues, JdbcClient jdbc,
                        BookingService bookingService) {
        this.events = events;
        this.venues = venues;
        this.jdbc = jdbc;
        this.bookingService = bookingService;
    }

    @Transactional(readOnly = true)
    public List<VenueResponse> listVenues() {
        return venues.findAllByOrderByCityAsc().stream().map(VenueResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listPublished() {
        return events.findByStatusOrderByStartsAtAsc(EventStatus.PUBLISHED).stream()
                .map(EventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventResponse getPublished(Long id) {
        return events.findByIdAndStatus(id, EventStatus.PUBLISHED)
                .map(event -> EventResponse.from(event, performers(event.getId())))
                .orElseThrow(() -> new EntityNotFoundException("event not found"));
    }

    @Transactional(readOnly = true)
    public Optional<List<AreaCount>> areaCounts(Long eventId, String known) {
        String current = String.join(",", jdbc.sql("""
                        SELECT ea.area_id || ':' || ea.version FROM event_area ea
                        JOIN area a ON a.id = ea.area_id
                        JOIN event e ON e.id = ea.event_id
                        WHERE ea.event_id = ? AND e.status = 'PUBLISHED'
                        ORDER BY a.rank
                        """)
                .param(eventId)
                .query(String.class).list());
        if (current.equals(known)) {
            return Optional.empty();
        }
        return Optional.of(jdbc.sql("""
                        WITH free AS (
                            SELECT s.area_id, s.row_rank,
                                   s.number - row_number() OVER (PARTITION BY s.area_id, s.row_rank ORDER BY s.number) AS run
                            FROM event_area ea JOIN seat s ON s.area_id = ea.area_id
                            WHERE ea.event_id = :event AND NOT EXISTS (
                                SELECT 1 FROM ticket t WHERE t.event_id = ea.event_id AND t.seat_id = s.id
                                AND t.status IN ('HELD', 'VALID', 'USED'))
                        ), runs AS (
                            SELECT area_id, count(*) AS length FROM free GROUP BY area_id, row_rank, run
                        )
                        SELECT a.id AS area_id, a.name, ea.version,
                               (SELECT count(*) FROM seat s WHERE s.area_id = a.id) AS total,
                               (SELECT count(*) FROM free f WHERE f.area_id = a.id) AS free,
                               coalesce((SELECT max(length) FROM runs r WHERE r.area_id = a.id), 0) AS longest_run,
                               ea.price_paise
                        FROM event_area ea
                        JOIN area a ON a.id = ea.area_id
                        JOIN event e ON e.id = ea.event_id
                        WHERE ea.event_id = :event AND e.status = 'PUBLISHED'
                        ORDER BY a.rank
                        """)
                .param("event", eventId)
                .query(AreaCount.class).list());
    }

    @Transactional(readOnly = true)
    public List<SeatDot> areaMap(Long eventId, Long areaId) {
        return jdbc.sql("""
                        SELECT s.id, s.row_label, s.row_rank, s.number,
                               EXISTS (SELECT 1 FROM ticket t WHERE t.event_id = ea.event_id AND t.seat_id = s.id
                                       AND t.status IN ('HELD', 'VALID', 'USED')) AS taken
                        FROM event_area ea
                        JOIN event e ON e.id = ea.event_id
                        JOIN seat s ON s.area_id = ea.area_id
                        WHERE ea.event_id = ? AND ea.area_id = ? AND e.status IN ('PUBLISHED', 'CANCELLED')
                        ORDER BY s.row_rank, s.number
                        """)
                .params(eventId, areaId)
                .query(SeatDot.class).list();
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listMine(Long organizerId) {
        return events.findByOrganizerIdOrderByStartsAtAsc(organizerId).stream()
                .map(EventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventResponse getMine(Long id, Long organizerId) {
        return EventResponse.from(findMine(id, organizerId), performers(id));
    }

    private List<Performer> performers(long eventId) {
        return jdbc.sql("SELECT name, role FROM performer WHERE event_id = ? ORDER BY position")
                .param(eventId)
                .query(Performer.class).list();
    }

    @Transactional(readOnly = true)
    public EventReport report(Long id, Long organizerId) {
        findMine(id, organizerId);
        List<EventReport.AreaLine> areas = jdbc.sql("""
                        SELECT a.id AS area_id, a.name,
                               (SELECT count(*) FROM seat s WHERE s.area_id = a.id) AS total,
                               (SELECT count(*) FROM ticket t JOIN seat s ON s.id = t.seat_id
                                WHERE t.event_id = ea.event_id AND s.area_id = a.id AND t.status IN ('HELD', 'VALID', 'USED')) AS booked,
                               (SELECT count(*) FROM queue_request q
                                WHERE q.event_id = ea.event_id AND q.area_id = a.id AND q.status = 'WAITING') AS queue_length,
                               (SELECT count(*) FROM ticket t JOIN seat s ON s.id = t.seat_id
                                WHERE t.event_id = ea.event_id AND s.area_id = a.id AND t.status = 'USED') AS checked_in
                        FROM event_area ea JOIN area a ON a.id = ea.area_id
                        WHERE ea.event_id = ? ORDER BY a.rank
                        """)
                .param(id)
                .query(EventReport.AreaLine.class).list();

        List<EventReport.Attendee> attendees = jdbc.sql("""
                        SELECT t.attendee_name, a.name AS area_name, s.row_label, s.number, t.status,
                               u.username AS booked_by
                        FROM ticket t
                        JOIN seat s ON s.id = t.seat_id
                        JOIN area a ON a.id = s.area_id
                        JOIN booking b ON b.id = t.booking_id
                        JOIN users u ON u.id = b.user_id
                        WHERE t.event_id = ? AND t.status IN ('HELD', 'VALID', 'USED')
                        ORDER BY a.rank, s.row_rank, s.number
                        """)
                .param(id)
                .query(EventReport.Attendee.class).list();
        return new EventReport(areas, attendees);
    }

    @Transactional
    public EventResponse createDraft(Long organizerId, CreateEventRequest request) {
        validate(request);
        Venue venue = venues.findById(request.venueId())
                .orElseThrow(() -> new EventValidationException(Map.of("venueId", "Pick a venue from the list.")));
        Event event = events.save(new Event(venue, organizerId, request.title().trim(), request.startsAt(),
                request.endsAt(), request.bookingOpensAt(), request.bookingClosesAt(), request.maxPerPerson(),
                request.category().trim(), request.tagline().trim(), request.description().trim(),
                request.presentedBy().trim(), request.language().trim(), request.ageGuidance().trim()));
        List<Performer> lineup = request.performers() == null ? List.of() : request.performers();
        for (int i = 0; i < lineup.size(); i++) {
            jdbc.sql("INSERT INTO performer (event_id, position, name, role) VALUES (?, ?, ?, ?)")
                    .params(event.getId(), i + 1, lineup.get(i).name().trim(), lineup.get(i).role().trim())
                    .update();
        }
        return EventResponse.from(event, performers(event.getId()));
    }

    @Transactional
    public EventResponse publish(Long id, Long organizerId) {
        Event event = findMine(id, organizerId);
        if (!event.getStatus().canMoveTo(EventStatus.PUBLISHED)) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a draft can be published.");
        }
        venues.lock(event.getVenue().getId());
        events.findClash(id, event.getVenue().getId(), event.getStartsAt(), event.getEndsAt())
                .ifPresent(title -> {
                    throw new ApiException(HttpStatus.CONFLICT,
                            "The hall is taken by " + title + " at that time. Pick another time.");
                });
        if (events.publish(id) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a draft can be published.");
        }
        events.createAreas(id);
        return EventResponse.from(findMine(id, organizerId));
    }

    @Transactional
    public EventResponse cancel(Long id, Long organizerId) {
        events.lock(id);
        Event event = findMine(id, organizerId);
        if (!event.getStatus().canMoveTo(EventStatus.CANCELLED) || events.cancel(id) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "This event is already cancelled.");
        }
        bookingService.cancelEventTickets(id);
        return EventResponse.from(findMine(id, organizerId));
    }

    private Event findMine(Long id, Long organizerId) {
        return events.findByIdAndOrganizerId(id, organizerId)
                .orElseThrow(() -> new EntityNotFoundException("event not found"));
    }

    private void validate(CreateEventRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!request.endsAt().isAfter(request.startsAt())) {
            errors.put("endsAt", "End time must be after the start time.");
        }
        if (!request.bookingClosesAt().isAfter(request.bookingOpensAt())) {
            errors.put("bookingClosesAt", "Booking must close after it opens.");
        }
        if (!errors.isEmpty()) {
            throw new EventValidationException(errors);
        }
    }
}

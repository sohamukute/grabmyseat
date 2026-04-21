package com.grabmyseat.inventory.repository;

import com.grabmyseat.inventory.model.Event;
import com.grabmyseat.inventory.model.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByStatusOrderByStartsAtAsc(EventStatus status);

    List<Event> findByOrganizerIdOrderByStartsAtAsc(Long organizerId);

    Optional<Event> findByIdAndOrganizerId(Long id, Long organizerId);

    Optional<Event> findByIdAndStatus(Long id, EventStatus status);

    @Query(value = """
            SELECT title FROM event
            WHERE venue_id = :venueId AND status = 'PUBLISHED' AND id <> :id
            AND starts_at < :endsAt AND ends_at > :startsAt
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findClash(Long id, Long venueId, Instant startsAt, Instant endsAt);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE event SET status = 'PUBLISHED' WHERE id = :id AND status = 'DRAFT'", nativeQuery = true)
    int publish(Long id);

    @Query(value = "SELECT id FROM event WHERE id = :id FOR UPDATE", nativeQuery = true)
    Long lock(Long id);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE event SET status = 'CANCELLED' WHERE id = :id AND status IN ('DRAFT', 'PUBLISHED')", nativeQuery = true)
    int cancel(Long id);

    @Modifying
    @Query(value = """
            INSERT INTO event_area (event_id, area_id)
            SELECT e.id, a.id FROM event e JOIN area a ON a.venue_id = e.venue_id
            WHERE e.id = :id
            """, nativeQuery = true)
    int createAreas(Long id);
}

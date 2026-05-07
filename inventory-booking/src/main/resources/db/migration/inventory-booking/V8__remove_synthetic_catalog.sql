-- V6 populated a presentation-only catalogue. Fresh and existing installations must start empty;
-- organisers now create their own events. Keep real operational records safe if an old demo event
-- has already acquired a reservation or a gate-team assignment.
DELETE FROM events event
WHERE ((event.organizer_id = 0
        OR event.name LIKE 'Load Test %'
        OR event.name = 'Queue Capacity Test')
       OR (event.name = 'Euphoria'
           AND event.venue = 'Mahalaxmi Racecourse, Mumbai'
           AND event.organizer_id = 4824))
  AND NOT EXISTS (SELECT 1 FROM reservations reservation WHERE reservation.event_id = event.id)
  AND NOT EXISTS (SELECT 1 FROM staff_assignments assignment WHERE assignment.event_id = event.id);

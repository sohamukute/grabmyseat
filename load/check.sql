SELECT count(*) FILTER (WHERE status = 'VALID') AS seats_sold,
       (SELECT count(*) FROM seat s JOIN event_area ea ON ea.area_id = s.area_id WHERE ea.event_id = :event) AS capacity
FROM ticket WHERE event_id = :event;

SELECT count(*) AS seats_sold_twice FROM (
    SELECT seat_id FROM ticket WHERE event_id = :event AND status IN ('VALID', 'USED')
    GROUP BY seat_id HAVING count(*) > 1) twice;

SELECT count(*) AS users_over_limit FROM (
    SELECT b.user_id FROM ticket t JOIN booking b ON b.id = t.booking_id
    WHERE t.event_id = :event AND t.status IN ('VALID', 'USED')
    GROUP BY b.user_id HAVING count(*) > (SELECT max_per_person FROM event WHERE id = :event)) over;

SELECT count(*) AS duplicate_requests FROM (
    SELECT user_id, request_id FROM booking WHERE event_id = :event GROUP BY 1, 2 HAVING count(*) > 1) dup;

SELECT count(*) AS seated_from_line,
       count(*) FILTER (WHERE (SELECT count(*) FROM ticket t WHERE t.booking_id = q.booking_id) <> q.group_size) AS wrong_size
FROM queue_request q WHERE q.event_id = :event AND q.status = 'SEATED';

SELECT count(*) AS bookings_without_email FROM booking b
WHERE b.event_id = :event AND NOT EXISTS (SELECT 1 FROM notification n WHERE n.ref_id = b.id AND n.kind IN ('BOOKED', 'SEATED'));

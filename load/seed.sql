INSERT INTO users (username, password_hash, email, email_verified_at)
SELECT 'k6user' || n, '$2y$10$j/M/jq8BgMViC6q0D848xOkq27qcKzm.J3fTrmudSNf9f7rY0Nqy.', 'k6user' || n || '@example.com', now()
FROM generate_series(1, 1000) n
ON CONFLICT (username) DO NOTHING;

DELETE FROM notification WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'k6user%');
DELETE FROM waiting_room WHERE event_id = :event;
DELETE FROM queue_request WHERE event_id = :event;
DELETE FROM ticket WHERE event_id = :event;
DELETE FROM booking WHERE event_id = :event;
DELETE FROM rate_hit WHERE key LIKE 'book:%';
UPDATE event_area SET version = version + 1 WHERE event_id = :event;
UPDATE event SET booking_opens_at = now() + interval '45 seconds' WHERE id = :event;

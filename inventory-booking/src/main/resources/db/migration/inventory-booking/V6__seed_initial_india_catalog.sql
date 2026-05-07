-- Operational starter catalogue. All entries are standard-sale events; flash sales
-- are created deliberately by an organiser and never appear as seeded promotions.
DO $$
DECLARE
    event_id BIGINT;
    zone_id BIGINT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM events WHERE name = 'City Lights: A Comedy Night') THEN
        INSERT INTO events (name, venue, starts_at, ends_at, sale_starts_at, sale_type, organizer_id)
        VALUES ('City Lights: A Comedy Night', 'Bal Gandharva Rang Mandir, Mumbai', now() + interval '18 days', now() + interval '18 days 3 hours', now() - interval '1 day', 'STANDARD', 0)
        RETURNING id INTO event_id;
        INSERT INTO zones (event_id, name, capacity, price) VALUES (event_id, 'Balcony', 80, 699) RETURNING id INTO zone_id;
        INSERT INTO seats (zone_id, row_label, number) SELECT zone_id, 'GA', n FROM generate_series(1, 80) AS n;
        INSERT INTO zones (event_id, name, capacity, price) VALUES (event_id, 'Main Hall', 140, 1199) RETURNING id INTO zone_id;
        INSERT INTO seats (zone_id, row_label, number) SELECT zone_id, 'GA', n FROM generate_series(1, 140) AS n;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM events WHERE name = 'Monsoon Theatre: The Last Letter') THEN
        INSERT INTO events (name, venue, starts_at, ends_at, sale_starts_at, sale_type, organizer_id)
        VALUES ('Monsoon Theatre: The Last Letter', 'Kamani Auditorium, New Delhi', now() + interval '31 days', now() + interval '31 days 2 hours 30 minutes', now() + interval '4 days', 'STANDARD', 0)
        RETURNING id INTO event_id;
        INSERT INTO zones (event_id, name, capacity, price) VALUES (event_id, 'Upper Circle', 120, 850) RETURNING id INTO zone_id;
        INSERT INTO seats (zone_id, row_label, number) SELECT zone_id, 'GA', n FROM generate_series(1, 120) AS n;
        INSERT INTO zones (event_id, name, capacity, price) VALUES (event_id, 'Orchestra', 160, 1499) RETURNING id INTO zone_id;
        INSERT INTO seats (zone_id, row_label, number) SELECT zone_id, 'GA', n FROM generate_series(1, 160) AS n;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM events WHERE name = 'Bengaluru FC v Coastal United') THEN
        INSERT INTO events (name, venue, starts_at, ends_at, sale_starts_at, sale_type, organizer_id)
        VALUES ('Bengaluru FC v Coastal United', 'Sree Kanteerava Stadium, Bengaluru', now() + interval '45 days', now() + interval '45 days 3 hours', now() + interval '18 days', 'STANDARD', 0)
        RETURNING id INTO event_id;
        INSERT INTO zones (event_id, name, capacity, price) VALUES (event_id, 'North Stand', 180, 499) RETURNING id INTO zone_id;
        INSERT INTO seats (zone_id, row_label, number) SELECT zone_id, 'GA', n FROM generate_series(1, 180) AS n;
        INSERT INTO zones (event_id, name, capacity, price) VALUES (event_id, 'East Stand', 220, 799) RETURNING id INTO zone_id;
        INSERT INTO seats (zone_id, row_label, number) SELECT zone_id, 'GA', n FROM generate_series(1, 220) AS n;
    END IF;
END $$;

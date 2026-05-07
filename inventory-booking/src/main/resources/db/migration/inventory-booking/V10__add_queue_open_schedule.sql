ALTER TABLE events ADD COLUMN queue_opens_at TIMESTAMP WITH TIME ZONE;

UPDATE events
SET queue_opens_at = CASE
    WHEN sale_type = 'FLASH' THEN sale_starts_at - INTERVAL '30 minutes'
    ELSE sale_starts_at
END;

ALTER TABLE events ALTER COLUMN queue_opens_at SET NOT NULL;

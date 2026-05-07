ALTER TABLE events
    ADD COLUMN sale_starts_at TIMESTAMPTZ,
    ADD COLUMN sale_ends_at TIMESTAMPTZ,
    ADD COLUMN sale_type VARCHAR(16) NOT NULL DEFAULT 'STANDARD';

UPDATE events
SET sale_starts_at = created_at
WHERE sale_starts_at IS NULL;

ALTER TABLE events
    ALTER COLUMN sale_starts_at SET NOT NULL;

CREATE INDEX idx_events_sale_starts_at ON events(sale_starts_at);

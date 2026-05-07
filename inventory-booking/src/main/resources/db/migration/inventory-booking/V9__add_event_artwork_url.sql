-- Artwork is a durable URL owned by inventory. Binary files belong in object storage, not this database.
ALTER TABLE events ADD COLUMN artwork_url VARCHAR(2048);

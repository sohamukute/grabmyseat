ALTER TABLE tickets ADD COLUMN holder_name VARCHAR(120) NOT NULL DEFAULT 'Guest';

CREATE TABLE ticket_attendees (
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    attendee_index INT NOT NULL,
    attendee_name VARCHAR(120) NOT NULL,
    PRIMARY KEY (ticket_id, attendee_index),
    CONSTRAINT uk_ticket_attendee_name UNIQUE (ticket_id, attendee_name)
);

CREATE TABLE ticket_attendance (
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    attendee_name VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY (ticket_id, attendee_name)
);

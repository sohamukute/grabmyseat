CREATE TABLE reservation_attendees (
    reservation_id BIGINT NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    attendee_index INT NOT NULL,
    attendee_name VARCHAR(120) NOT NULL,
    PRIMARY KEY (reservation_id, attendee_index)
);

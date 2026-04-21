package com.grabmyseat.inventory.service;

import com.grabmyseat.auth.web.ApiException;
import com.grabmyseat.inventory.dto.StaffMember;
import com.grabmyseat.inventory.repository.EventRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StaffService {

    private final EventRepository events;
    private final JdbcClient jdbc;

    public StaffService(EventRepository events, JdbcClient jdbc) {
        this.events = events;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<StaffMember> list(long eventId, long organizerId) {
        requireOwner(eventId, organizerId);
        return jdbc.sql("""
                        SELECT u.id AS user_id, u.username FROM staff_assignment sa
                        JOIN users u ON u.id = sa.user_id
                        WHERE sa.event_id = ? ORDER BY u.username
                        """)
                .param(eventId)
                .query(StaffMember.class).list();
    }

    @Transactional
    public List<StaffMember> add(long eventId, long organizerId, String username) {
        requireOwner(eventId, organizerId);
        long userId = jdbc.sql("SELECT id FROM users WHERE username = ?")
                .param(username.trim())
                .query(Long.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No account has that username. Check the spelling."));
        jdbc.sql("INSERT INTO staff_assignment (event_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING")
                .params(eventId, userId)
                .update();
        return list(eventId, organizerId);
    }

    @Transactional
    public List<StaffMember> remove(long eventId, long organizerId, long userId) {
        requireOwner(eventId, organizerId);
        jdbc.sql("DELETE FROM staff_assignment WHERE event_id = ? AND user_id = ?")
                .params(eventId, userId)
                .update();
        return list(eventId, organizerId);
    }

    private void requireOwner(long eventId, long organizerId) {
        events.findByIdAndOrganizerId(eventId, organizerId)
                .orElseThrow(() -> new EntityNotFoundException("event not found"));
    }
}

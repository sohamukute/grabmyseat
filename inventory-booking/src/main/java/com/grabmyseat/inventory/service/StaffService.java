package com.grabmyseat.inventory.service;

import com.grabmyseat.inventory.client.AuthServiceClient;
import com.grabmyseat.inventory.dto.StaffAssignmentResponse;
import com.grabmyseat.inventory.model.Event;
import com.grabmyseat.inventory.model.StaffAssignment;
import com.grabmyseat.inventory.model.StaffAssignmentStatus;
import com.grabmyseat.inventory.repository.EventRepository;
import com.grabmyseat.inventory.repository.StaffAssignmentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class StaffService {

    private final EventRepository eventRepository;
    private final StaffAssignmentRepository staffAssignments;
    private final AuthServiceClient authServiceClient;

    public StaffService(EventRepository eventRepository,
                        StaffAssignmentRepository staffAssignments,
                        AuthServiceClient authServiceClient) {
        this.eventRepository = eventRepository;
        this.staffAssignments = staffAssignments;
        this.authServiceClient = authServiceClient;
    }

    @Transactional
    public StaffAssignmentResponse inviteStaff(Long eventId, Long organizerId, String username) {
        AuthServiceClient.LookupResult user = authServiceClient.lookupByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("no account found for username: " + username);
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("event not found"));
        if (!event.getOrganizerId().equals(organizerId)) {
            throw new SecurityException("not the organizer of this event");
        }

        Optional<StaffAssignment> existing = staffAssignments.findByEventIdAndUserId(eventId, user.userId());
        StaffAssignment assignment;
        if (existing.isPresent()) {
            assignment = existing.get();
            if (assignment.getStatus() == StaffAssignmentStatus.ACTIVE) {
                return StaffAssignmentResponse.from(assignment, user.username());
            }
            assignment.setStatus(StaffAssignmentStatus.ACTIVE);
            assignment.setRespondedAt(Instant.now());
        } else {
            assignment = new StaffAssignment(event, user.userId(), StaffAssignmentStatus.ACTIVE, organizerId);
        }

        assignment = staffAssignments.save(assignment);

        // Idempotent: auth-service ignores duplicate role grants.
        authServiceClient.grantRole(user.userId(), "ROLE_STAFF");
        return StaffAssignmentResponse.from(assignment, user.username());
    }

    @Transactional
    public StaffAssignmentResponse requestStaff(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("event not found"));
        StaffAssignment assignment = staffAssignments.findByEventIdAndUserId(eventId, userId)
                .orElseGet(() -> new StaffAssignment(event, userId, StaffAssignmentStatus.PENDING, event.getOrganizerId()));
        if (assignment.getStatus() == StaffAssignmentStatus.REVOKED) {
            assignment.setStatus(StaffAssignmentStatus.PENDING);
            assignment.setRespondedAt(null);
        }
        return StaffAssignmentResponse.from(staffAssignments.save(assignment));
    }

    @Transactional
    public StaffAssignmentResponse approveStaff(Long eventId, Long staffUserId, Long organizerId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("event not found"));
        if (!event.getOrganizerId().equals(organizerId)) {
            throw new SecurityException("not the organizer of this event");
        }
        StaffAssignment assignment = staffAssignments.findByEventIdAndUserId(eventId, staffUserId)
                .orElseThrow(() -> new EntityNotFoundException("staff request not found"));
        if (assignment.getStatus() != StaffAssignmentStatus.PENDING) {
            throw new IllegalStateException("staff request is not pending");
        }
        assignment.setStatus(StaffAssignmentStatus.ACTIVE);
        assignment.setRespondedAt(Instant.now());
        authServiceClient.grantRole(staffUserId, "ROLE_STAFF");
        return StaffAssignmentResponse.from(staffAssignments.save(assignment));
    }

    @Transactional(readOnly = true)
    public List<StaffAssignmentResponse> listStaff(Long eventId, Long organizerId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("event not found"));
        if (!event.getOrganizerId().equals(organizerId)) {
            throw new SecurityException("not the organizer of this event");
        }

        return staffAssignments.findByEventId(eventId).stream()
                .map(assignment -> {
                    AuthServiceClient.DisplayUser user = authServiceClient.lookupDisplayById(assignment.getUserId());
                    return StaffAssignmentResponse.from(assignment, user == null ? null : user.username());
                })
                .toList();
    }

    @Transactional
    public StaffAssignmentResponse revokeStaff(Long eventId, Long userId, Long organizerId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("event not found"));
        if (!event.getOrganizerId().equals(organizerId)) {
            throw new SecurityException("not the organizer of this event");
        }

        StaffAssignment assignment = staffAssignments.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new EntityNotFoundException("staff assignment not found"));

        assignment.setStatus(StaffAssignmentStatus.REVOKED);
        assignment.setRespondedAt(Instant.now());
        return StaffAssignmentResponse.from(assignment);
    }
}

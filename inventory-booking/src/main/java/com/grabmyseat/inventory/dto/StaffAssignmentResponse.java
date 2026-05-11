package com.grabmyseat.inventory.dto;

import com.grabmyseat.inventory.model.StaffAssignment;

import java.time.Instant;

public record StaffAssignmentResponse(
        Long id,
        Long eventId,
        Long userId,
        String username,
        String status,
        Long invitedBy,
        Instant invitedAt,
        Instant respondedAt
) {

    public StaffAssignmentResponse(Long id, Long eventId, Long userId, String status, Long invitedBy,
                                   Instant invitedAt, Instant respondedAt) {
        this(id, eventId, userId, null, status, invitedBy, invitedAt, respondedAt);
    }

    public static StaffAssignmentResponse from(StaffAssignment assignment) {
        return from(assignment, null);
    }

    public static StaffAssignmentResponse from(StaffAssignment assignment, String username) {
        return new StaffAssignmentResponse(
                assignment.getId(),
                assignment.getEvent().getId(),
                assignment.getUserId(),
                username,
                assignment.getStatus().name(),
                assignment.getInvitedBy(),
                assignment.getInvitedAt(),
                assignment.getRespondedAt()
        );
    }
}

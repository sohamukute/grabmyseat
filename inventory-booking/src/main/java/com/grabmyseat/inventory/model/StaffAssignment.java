package com.grabmyseat.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "staff_assignments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "user_id"}))
public class StaffAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private StaffAssignmentStatus status;

    @Column(name = "invited_by", nullable = false)
    private Long invitedBy;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt = Instant.now();

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected StaffAssignment() {
    }

    public StaffAssignment(Event event, Long userId, StaffAssignmentStatus status, Long invitedBy) {
        this.event = event;
        this.userId = userId;
        this.status = status;
        this.invitedBy = invitedBy;
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public Long getUserId() {
        return userId;
    }

    public StaffAssignmentStatus getStatus() {
        return status;
    }

    public void setStatus(StaffAssignmentStatus status) {
        this.status = status;
    }

    public Long getInvitedBy() {
        return invitedBy;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }
}

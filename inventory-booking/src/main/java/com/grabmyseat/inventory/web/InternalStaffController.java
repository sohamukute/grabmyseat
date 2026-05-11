package com.grabmyseat.inventory.web;

import com.grabmyseat.inventory.dto.StaffAssignmentCheckResponse;
import com.grabmyseat.inventory.model.Event;
import com.grabmyseat.inventory.model.StaffAssignmentStatus;
import com.grabmyseat.inventory.repository.EventRepository;
import com.grabmyseat.inventory.repository.StaffAssignmentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/internal")
public class InternalStaffController {

    private final StaffAssignmentRepository staffAssignmentRepository;
    private final EventRepository eventRepository;

    public InternalStaffController(StaffAssignmentRepository staffAssignmentRepository,
                                   EventRepository eventRepository) {
        this.staffAssignmentRepository = staffAssignmentRepository;
        this.eventRepository = eventRepository;
    }

    @GetMapping("/events/{eventId}/staff/{userId}")
    public ResponseEntity<StaffAssignmentCheckResponse> checkAssignment(@PathVariable Long eventId,
                                                                        @PathVariable Long userId) {
        boolean active = staffAssignmentRepository
                .existsByEventIdAndUserIdAndStatus(eventId, userId, StaffAssignmentStatus.ACTIVE);
        boolean organizer = eventRepository.findById(eventId)
                .map(Event::getOrganizerId)
                .map(id -> id.equals(userId))
                .orElse(false);
        return ResponseEntity.ok(new StaffAssignmentCheckResponse(active, organizer));
    }
}

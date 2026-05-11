package com.grabmyseat.inventory.web;

import com.grabmyseat.inventory.dto.InviteStaffRequest;
import com.grabmyseat.inventory.dto.StaffAssignmentResponse;
import com.grabmyseat.inventory.security.UserContext;
import com.grabmyseat.inventory.service.StaffService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/events/{eventId}/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @PostMapping
    public ResponseEntity<StaffAssignmentResponse> invite(HttpServletRequest request,
                                                          @PathVariable Long eventId,
                                                          @Valid @RequestBody InviteStaffRequest body) {
        UserContext user = requireOrganizer(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(staffService.inviteStaff(eventId, user.userId(), body.username()));
    }

    @PostMapping("/request")
    public ResponseEntity<StaffAssignmentResponse> requestAssignment(HttpServletRequest request,
                                                                      @PathVariable Long eventId) {
        UserContext user = UserContext.fromRequest(request);
        if (user.userId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.requestStaff(eventId, user.userId()));
    }

    @PostMapping("/{userId}/approve")
    public StaffAssignmentResponse approve(HttpServletRequest request,
                                           @PathVariable Long eventId,
                                           @PathVariable Long userId) {
        UserContext organizer = requireOrganizer(request);
        return staffService.approveStaff(eventId, userId, organizer.userId());
    }

    @GetMapping
    public List<StaffAssignmentResponse> list(HttpServletRequest request, @PathVariable Long eventId) {
        UserContext user = requireOrganizer(request);
        return staffService.listStaff(eventId, user.userId());
    }

    @PostMapping("/{userId}/revoke")
    public StaffAssignmentResponse revoke(HttpServletRequest request,
                                          @PathVariable Long eventId,
                                          @PathVariable Long userId) {
        UserContext user = requireOrganizer(request);
        return staffService.revokeStaff(eventId, userId, user.userId());
    }

    private UserContext requireOrganizer(HttpServletRequest request) {
        UserContext user = UserContext.fromRequest(request);
        if (user.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing user identity");
        }
        if (!user.isOrganizer()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only organizers can manage staff");
        }
        return user;
    }
}

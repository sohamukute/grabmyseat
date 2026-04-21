package com.grabmyseat.inventory.web;

import com.grabmyseat.inventory.dto.InviteStaffRequest;
import com.grabmyseat.inventory.dto.StaffMember;
import com.grabmyseat.inventory.security.UserContext;
import com.grabmyseat.inventory.service.StaffService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/organizer/events/{eventId}/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public List<StaffMember> list(@PathVariable long eventId, HttpServletRequest request) {
        return staffService.list(eventId, organizerId(request));
    }

    @PostMapping
    public List<StaffMember> add(@PathVariable long eventId, @Valid @RequestBody InviteStaffRequest body,
                                 HttpServletRequest request) {
        return staffService.add(eventId, organizerId(request), body.username());
    }

    @DeleteMapping("/{userId}")
    public List<StaffMember> remove(@PathVariable long eventId, @PathVariable long userId,
                                    HttpServletRequest request) {
        return staffService.remove(eventId, organizerId(request), userId);
    }

    private long organizerId(HttpServletRequest request) {
        UserContext user = UserContext.fromRequest(request);
        if (!user.isOrganizer()) {
            throw new AccessDeniedException("organizer only");
        }
        return user.userId();
    }
}

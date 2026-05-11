package com.grabmyseat.inventory.web;

import com.grabmyseat.inventory.dto.AttendeeRequest;
import com.grabmyseat.inventory.dto.AttendeeResponse;
import com.grabmyseat.inventory.security.UserContext;
import com.grabmyseat.inventory.service.AttendeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/attendees")
public class AttendeeController {

    private final AttendeeService attendeeService;

    public AttendeeController(AttendeeService attendeeService) {
        this.attendeeService = attendeeService;
    }

    @GetMapping
    public ResponseEntity<List<AttendeeResponse>> list(HttpServletRequest request) {
        Long userId = requireUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(attendeeService.list(userId));
    }

    @PostMapping
    public ResponseEntity<AttendeeResponse> create(HttpServletRequest request, @Valid @RequestBody AttendeeRequest body) {
        Long userId = requireUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(attendeeService.create(userId, body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AttendeeResponse> update(HttpServletRequest request, @PathVariable Long id,
                                                    @Valid @RequestBody AttendeeRequest body) {
        Long userId = requireUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(attendeeService.update(id, userId, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(HttpServletRequest request, @PathVariable Long id) {
        Long userId = requireUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        attendeeService.remove(id, userId);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(HttpServletRequest request) {
        return UserContext.fromRequest(request).userId();
    }
}

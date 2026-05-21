package com.grabmyseat.ticketing.web;

import com.grabmyseat.ticketing.client.StaffAssignmentClient;
import com.grabmyseat.ticketing.dto.TicketResponse;
import com.grabmyseat.ticketing.dto.CheckInRequest;
import com.grabmyseat.ticketing.model.Ticket;
import com.grabmyseat.ticketing.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.Optional;
import java.util.List;

@RestController
@RequestMapping("/api/ticketing/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final StaffAssignmentClient staffAssignmentClient;

    public TicketController(TicketService ticketService, StaffAssignmentClient staffAssignmentClient) {
        this.ticketService = ticketService;
        this.staffAssignmentClient = staffAssignmentClient;
    }

    @Operation(summary = "List tickets belonging to the authenticated customer")
    @GetMapping("/mine")
    public ResponseEntity<List<TicketResponse>> mine(HttpServletRequest request) {
        Long userId = extractUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(ticketService.findByUserId(userId).stream()
                .map(TicketResponse::from)
                .toList());
    }

    @Operation(summary = "Get a ticket by reservation token")
    @GetMapping("/{token}")
    public ResponseEntity<TicketResponse> get(HttpServletRequest request, @PathVariable String token) {
        Long userId = extractUserId(request);
        Optional<Ticket> ticketOpt = ticketService.findByToken(token);
        if (ticketOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Ticket ticket = ticketOpt.get();
        if (!ticket.getUserId().equals(userId) && !hasRole(request, "ROLE_ORGANIZER") && !hasRole(request, "ROLE_STAFF")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }

    @Operation(summary = "Regenerate the QR payload for an active ticket")
    @PostMapping("/{token}/regenerate")
    public ResponseEntity<TicketResponse> regenerate(HttpServletRequest request, @PathVariable String token) {
        Long userId = extractUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<Ticket> ticketOpt = ticketService.findByToken(token);
        if (ticketOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Ticket ticket = ticketOpt.get();
        if (!ticket.getUserId().equals(userId) && !hasRole(request, "ROLE_ORGANIZER") && !hasRole(request, "ROLE_STAFF")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Ticket updated = ticketService.regenerateQr(token);
        return ResponseEntity.ok(TicketResponse.from(updated));
    }

    @Operation(summary = "Validate a ticket at the gate without marking it used")
    @PostMapping("/{token}/validate")
    public ResponseEntity<TicketResponse> validate(HttpServletRequest request, @PathVariable String token) {
        Long userId = extractUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!hasRole(request, "ROLE_ORGANIZER") && !hasRole(request, "ROLE_STAFF")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Load ticket first to get eventId for staff assignment check
        Optional<Ticket> ticketOpt = ticketService.findByToken(token);
        if (ticketOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Ticket ticket = ticketOpt.get();
        // Organizer always may scan; staff must have an ACTIVE assignment for this event
        if (!staffAssignmentClient.isAuthorizedScanner(ticket.getEventId(), userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Ticket validated = ticketService.verify(token);
        return ResponseEntity.ok(TicketResponse.from(validated));
    }

    @Operation(summary = "Record a group ticket's admitted and absent attendees")
    @PostMapping("/{token}/check-in")
    public ResponseEntity<TicketResponse> checkIn(HttpServletRequest request, @PathVariable String token,
                                                   @Valid @RequestBody CheckInRequest body) {
        Long userId = extractUserId(request);
        if (userId == null || (!hasRole(request, "ROLE_ORGANIZER") && !hasRole(request, "ROLE_STAFF"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Ticket ticket = ticketService.findByToken(token).orElse(null);
        if (ticket == null) return ResponseEntity.notFound().build();
        if (!staffAssignmentClient.isAuthorizedScanner(ticket.getEventId(), userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(TicketResponse.from(ticketService.checkIn(token, body.attendeesPresent())));
    }

    private Long extractUserId(HttpServletRequest request) {
        String header = request.getHeader("X-User-Id");
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(header);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean hasRole(HttpServletRequest request, String role) {
        String rolesHeader = request.getHeader("X-User-Roles");
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return false;
        }
        return java.util.Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .anyMatch(role::equals);
    }
}

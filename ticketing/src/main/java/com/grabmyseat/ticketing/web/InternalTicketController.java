package com.grabmyseat.ticketing.web;

import com.grabmyseat.ticketing.dto.TicketRequest;
import com.grabmyseat.ticketing.dto.TicketResponse;
import com.grabmyseat.ticketing.model.Ticket;
import com.grabmyseat.ticketing.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ticketing/internal/tickets")
public class InternalTicketController {

    private final TicketService ticketService;

    public InternalTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Operation(summary = "Issue a ticket from a confirmed reservation (internal)")
    @PostMapping
    public ResponseEntity<TicketResponse> issue(HttpServletRequest request,
                                                @Valid @RequestBody TicketRequest req) {
        Long userId = extractUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Ticket ticket = ticketService.issue(userId, req.reservationToken(), req.eventId(),
                req.zoneId(), req.seatIds(), req.attendeeNames(), req.price());
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketResponse.from(ticket));
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
}

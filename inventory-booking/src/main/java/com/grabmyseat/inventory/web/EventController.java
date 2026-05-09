package com.grabmyseat.inventory.web;

import com.grabmyseat.inventory.dto.CreateEventRequest;
import com.grabmyseat.inventory.dto.EventResponse;
import com.grabmyseat.inventory.dto.SaleAccessResponse;
import com.grabmyseat.inventory.security.UserContext;
import com.grabmyseat.inventory.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @Operation(summary = "List all published events")
    @GetMapping
    public List<EventResponse> listEvents() {
        return eventService.listEvents();
    }

    @Operation(summary = "Get event details")
    @GetMapping("/{id}")
    public EventResponse getEvent(@PathVariable Long id) {
        return eventService.getEvent(id);
    }

    @Operation(summary = "Get the current queue and sale availability for an event")
    @GetMapping("/{id}/sale-access")
    public SaleAccessResponse saleAccess(@PathVariable Long id) {
        return eventService.saleAccess(id);
    }

    @Operation(summary = "Get zone details for an event")
    @GetMapping("/{eventId}/zones/{zoneId}")
    public EventResponse.ZoneResponse getZone(@PathVariable Long eventId, @PathVariable Long zoneId) {
        return eventService.getZone(eventId, zoneId);
    }

    @Operation(summary = "Create a new event with zones and seats")
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            HttpServletRequest request,
            @Valid @RequestBody CreateEventRequest req) {
        UserContext user = UserContext.fromRequest(request);
        requireOrganizer(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventService.createEvent(user.userId(), req));
    }

    @Operation(summary = "Update an event")
    @PutMapping("/{id}")
    public EventResponse updateEvent(
            @PathVariable Long id,
            HttpServletRequest request,
            @Valid @RequestBody CreateEventRequest req) {
        UserContext user = UserContext.fromRequest(request);
        requireOrganizer(user);
        return eventService.updateEvent(id, user.userId(), req);
    }

    @Operation(summary = "List events for the authenticated organizer")
    @GetMapping("/organizer/me")
    public List<EventResponse> listMyEvents(HttpServletRequest request) {
        UserContext user = UserContext.fromRequest(request);
        requireOrganizer(user);
        return eventService.listEventsByOrganizer(user.userId());
    }

    private void requireOrganizer(UserContext user) {
        if (user.userId() == null || !user.isOrganizer()) {
            throw new org.springframework.security.access.AccessDeniedException("organizer role required");
        }
    }
}

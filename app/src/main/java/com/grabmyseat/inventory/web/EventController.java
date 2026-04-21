package com.grabmyseat.inventory.web;

import com.grabmyseat.inventory.dto.AreaCount;
import com.grabmyseat.inventory.dto.CreateEventRequest;
import com.grabmyseat.inventory.dto.EventReport;
import com.grabmyseat.inventory.dto.EventResponse;
import com.grabmyseat.inventory.dto.SeatDot;
import com.grabmyseat.inventory.dto.VenueResponse;
import com.grabmyseat.inventory.security.UserContext;
import com.grabmyseat.inventory.service.EventService;
import com.grabmyseat.live.SeatStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class EventController {

    private final EventService eventService;
    private final SeatStream stream;

    public EventController(EventService eventService, SeatStream stream) {
        this.eventService = eventService;
        this.stream = stream;
    }

    @GetMapping("/venues")
    public List<VenueResponse> venues() {
        return eventService.listVenues();
    }

    @GetMapping("/events")
    public List<EventResponse> events() {
        return eventService.listPublished();
    }

    @GetMapping("/events/{id}")
    public EventResponse event(@PathVariable Long id) {
        return eventService.getPublished(id);
    }

    @GetMapping("/events/{id}/counts")
    public ResponseEntity<List<AreaCount>> counts(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "") String versions) {
        return eventService.areaCounts(id, versions)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping(value = "/events/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id) {
        eventService.getPublished(id);
        return stream.watch(id);
    }

    @GetMapping("/events/{id}/areas/{areaId}/map")
    public List<SeatDot> areaMap(@PathVariable Long id, @PathVariable Long areaId) {
        return eventService.areaMap(id, areaId);
    }

    @GetMapping("/organizer/events")
    public List<EventResponse> myEvents(HttpServletRequest request) {
        return eventService.listMine(organizerId(request));
    }

    @GetMapping("/organizer/events/{id}")
    public EventResponse myEvent(@PathVariable Long id, HttpServletRequest request) {
        return eventService.getMine(id, organizerId(request));
    }

    @GetMapping("/organizer/events/{id}/report")
    public EventReport report(@PathVariable Long id, HttpServletRequest request) {
        return eventService.report(id, organizerId(request));
    }

    @PostMapping("/organizer/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody CreateEventRequest body, HttpServletRequest request) {
        return eventService.createDraft(organizerId(request), body);
    }

    @PostMapping("/organizer/events/{id}/publish")
    public EventResponse publish(@PathVariable Long id, HttpServletRequest request) {
        return eventService.publish(id, organizerId(request));
    }

    @PostMapping("/organizer/events/{id}/cancel")
    public EventResponse cancel(@PathVariable Long id, HttpServletRequest request) {
        return eventService.cancel(id, organizerId(request));
    }

    private Long organizerId(HttpServletRequest request) {
        UserContext user = UserContext.fromRequest(request);
        if (!user.isOrganizer()) {
            throw new AccessDeniedException("organizer only");
        }
        return user.userId();
    }
}

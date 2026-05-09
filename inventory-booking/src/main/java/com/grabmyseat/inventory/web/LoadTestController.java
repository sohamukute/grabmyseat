package com.grabmyseat.inventory.web;

import com.grabmyseat.inventory.dto.CreateEventRequest;
import com.grabmyseat.inventory.dto.EventLayoutRequest;
import com.grabmyseat.inventory.dto.EventResponse;
import com.grabmyseat.inventory.model.SaleType;
import com.grabmyseat.inventory.service.EventService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/inventory/internal/load-test")
@Profile("load-test")
public class LoadTestController {

    private static final long ORGANIZER_ID = 1L;

    private final EventService eventService;

    public LoadTestController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping("/seed")
    public ResponseEntity<SeedResponse> seed(@RequestBody SeedRequest request) {
        int seatCount = request.seatCount() != null && request.seatCount() > 0 ? request.seatCount() : 100;
        Instant eventStartsAt = Instant.now().plusSeconds(3600);
        Instant saleStartsAt = Instant.now();
        EventLayoutRequest layout = new EventLayoutRequest(
                1, BigDecimal.valueOf(10.00),
                seatCount, BigDecimal.valueOf(10.00),
                1, BigDecimal.valueOf(10.00));
        CreateEventRequest eventRequest = new CreateEventRequest(
                "Load Test Event",
                "Load Test Venue",
                "https://images.example.invalid/load-test-event.jpg",
                eventStartsAt,
                eventStartsAt.plusSeconds(3600),
                null,
                saleStartsAt,
                eventStartsAt.minusSeconds(1),
                SaleType.STANDARD,
                layout,
                null
        );

        EventResponse event = eventService.createEvent(ORGANIZER_ID, eventRequest);
        EventResponse.ZoneResponse zone = event.zones().get(1);
        List<Long> seatIds = zone.seats().stream()
                .map(EventResponse.SeatResponse::id)
                .toList();

        return ResponseEntity.ok(new SeedResponse(event.id(), zone.id(), seatIds));
    }

    public record SeedRequest(Integer seatCount) {
    }

    public record SeedResponse(Long eventId, Long zoneId, List<Long> seatIds) {
    }
}

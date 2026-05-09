package com.grabmyseat.inventory.web;

import com.grabmyseat.inventory.dto.CreateEventRequest;
import com.grabmyseat.inventory.dto.EventLayoutRequest;
import com.grabmyseat.inventory.dto.EventResponse;
import com.grabmyseat.inventory.model.SaleType;
import com.grabmyseat.inventory.security.UserContext;
import com.grabmyseat.inventory.service.EventService;
import com.grabmyseat.waitingroom.RedisKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/inventory/load-test")
@Profile("load-test")
public class LoadTestSeedController {

    private final EventService eventService;
    private final RedisTemplate<String, String> redis;

    public LoadTestSeedController(EventService eventService, RedisTemplate<String, String> redis) {
        this.eventService = eventService;
        this.redis = redis;
    }

    @PostMapping("/seed-event")
    public ResponseEntity<SeedEventResponse> seedEvent(
            HttpServletRequest request,
            @Valid @RequestBody(required = false) SeedEventRequest body) {
        UserContext user = UserContext.fromRequest(request);
        if (user.userId() == null || !user.isOrganizer()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String name = body != null && body.name() != null ? body.name() : "Load Test Event";
        int capacity = body != null && body.capacity() != null ? body.capacity() : 100;
        BigDecimal price = body != null && body.price() != null ? body.price() : BigDecimal.valueOf(10.00);
        Instant startsAt = body != null && body.startsAt() != null ? body.startsAt() : Instant.now().plusSeconds(3600);

        EventLayoutRequest layout = new EventLayoutRequest(
                1, price,
                capacity, price,
                1, price);
        CreateEventRequest eventRequest = new CreateEventRequest(
                name,
                "Load Test Venue",
                "https://images.example.invalid/load-test-event.jpg",
                startsAt,
                startsAt.plusSeconds(7200),
                null,
                Instant.now(),
                startsAt.minusSeconds(1),
                SaleType.STANDARD,
                layout,
                null
        );

        EventResponse event = eventService.createEvent(user.userId(), eventRequest);
        EventResponse.ZoneResponse firstZone = event.zones().get(1);
        List<Long> seatIds = firstZone.seats().stream()
                .map(EventResponse.SeatResponse::id)
                .toList();

        return ResponseEntity.status(HttpStatus.CREATED).body(new SeedEventResponse(
                event.id(),
                firstZone.id(),
                firstZone.capacity(),
                firstZone.price(),
                seatIds
        ));
    }

    @GetMapping("/permit")
    public ResponseEntity<PermitResponse> permit(HttpServletRequest request,
                                                  @RequestParam String queueToken) {
        UserContext user = UserContext.fromRequest(request);
        if (user.userId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Object tokenUserId = redis.opsForHash().get(RedisKeys.TOKEN_META_PREFIX + queueToken, "userId");
        if (tokenUserId == null || !String.valueOf(tokenUserId).equals(String.valueOf(user.userId()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String permitToken = redis.opsForValue().get(RedisKeys.PERMIT_PREFIX + queueToken);
        if (permitToken == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(new PermitResponse(permitToken));
    }

    public record SeedEventRequest(String name, Integer capacity, BigDecimal price, Instant startsAt) {
    }

    public record SeedEventResponse(Long eventId, Long zoneId, Integer capacity, BigDecimal price, List<Long> seatIds) {
    }

    public record PermitResponse(String permitToken) {
    }
}

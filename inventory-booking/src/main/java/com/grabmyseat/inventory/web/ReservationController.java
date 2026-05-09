package com.grabmyseat.inventory.web;

import com.grabmyseat.inventory.dto.ReservationResponse;
import com.grabmyseat.inventory.dto.ReserveQuantityRequest;
import com.grabmyseat.inventory.security.UserContext;
import com.grabmyseat.inventory.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Operation(summary = "Hold seats for up to 5 minutes")
    @PostMapping
    public ResponseEntity<ReservationResponse> reserve(
            HttpServletRequest request,
            @Valid @RequestBody ReserveQuantityRequest req) {
        UserContext user = UserContext.fromRequest(request);
        if (user.userId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String permit = request.getHeader("X-Queue-Permit");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.reserveByQuantity(user.userId(), request.getHeader("X-User-Name"), permit,
                        req.eventId(), req.zoneId(), req.quantity(), req.attendeeNames()));
    }

    @Operation(summary = "Get reservation by token")
    @GetMapping("/{token}")
    public ResponseEntity<ReservationResponse> get(@PathVariable String token) {
        return reservationService.findByToken(token)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Confirm a held reservation")
    @PostMapping("/{token}/confirm")
    public ResponseEntity<Void> confirm(HttpServletRequest request, @PathVariable String token) {
        UserContext user = UserContext.fromRequest(request);
        if (user.userId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        reservationService.confirm(user.userId(), token);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Cancel a held or confirmed reservation")
    @PostMapping("/{token}/cancel")
    public ResponseEntity<Void> cancel(HttpServletRequest request, @PathVariable String token) {
        UserContext user = UserContext.fromRequest(request);
        if (user.userId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        reservationService.cancel(user.userId(), token);
        return ResponseEntity.noContent().build();
    }
}

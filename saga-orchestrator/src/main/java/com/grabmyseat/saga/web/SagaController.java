package com.grabmyseat.saga.web;

import com.grabmyseat.saga.dto.SagaStatusResponse;
import com.grabmyseat.saga.model.SagaInstance;
import com.grabmyseat.saga.model.SagaOutbox;
import com.grabmyseat.saga.repository.SagaOutboxRepository;
import com.grabmyseat.saga.security.UserContext;
import com.grabmyseat.saga.service.SagaException;
import com.grabmyseat.saga.service.SagaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/saga/bookings")
public class SagaController {

    private final SagaService sagaService;
    private final SagaOutboxRepository sagaOutboxRepository;

    public SagaController(SagaService sagaService, SagaOutboxRepository sagaOutboxRepository) {
        this.sagaService = sagaService;
        this.sagaOutboxRepository = sagaOutboxRepository;
    }

    @PostMapping("/{reservationToken}/confirm")
    public ResponseEntity<SagaStatusResponse> confirm(@PathVariable String reservationToken,
                                                       HttpServletRequest request) {
        UserContext ctx = UserContext.fromRequest(request);
        if (ctx.userId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        SagaInstance saga = sagaService.confirmBooking(ctx.userId(), reservationToken);
        return ResponseEntity.ok(toResponse(saga));
    }

    @PostMapping("/{reservationToken}/cancel")
    public ResponseEntity<SagaStatusResponse> cancel(@PathVariable String reservationToken,
                                                      HttpServletRequest request) {
        UserContext ctx = UserContext.fromRequest(request);
        if (ctx.userId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        SagaInstance saga = sagaService.cancelBooking(ctx.userId(), reservationToken);
        return ResponseEntity.ok(toResponse(saga));
    }

    @GetMapping("/{reservationToken}/status")
    public ResponseEntity<SagaStatusResponse> status(@PathVariable String reservationToken,
                                                      HttpServletRequest request) {
        UserContext ctx = UserContext.fromRequest(request);
        if (ctx.userId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        SagaInstance saga = sagaService.findByReservationToken(reservationToken)
                .orElseThrow(() -> new SagaException(SagaException.Code.RESERVATION_NOT_FOUND,
                        "saga not found"));
        if (!saga.getUserId().equals(ctx.userId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(toResponse(saga));
    }

    private SagaStatusResponse toResponse(SagaInstance saga) {
        String latestStep = sagaOutboxRepository.findBySagaInstanceIdOrderByIdAsc(saga.getId()).stream()
                .reduce((a, b) -> b)
                .map(SagaOutbox::getStep)
                .map(Enum::name)
                .orElse("UNKNOWN");
        return new SagaStatusResponse(saga.getReservationToken(), saga.getStatus(), latestStep);
    }
}

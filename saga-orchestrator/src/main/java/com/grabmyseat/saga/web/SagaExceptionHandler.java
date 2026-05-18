package com.grabmyseat.saga.web;

import com.grabmyseat.saga.service.SagaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class SagaExceptionHandler {

    @ExceptionHandler(SagaException.class)
    public ResponseEntity<Map<String, Object>> sagaException(SagaException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case INSUFFICIENT_FUNDS -> HttpStatus.PAYMENT_REQUIRED;
            case RESERVATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RESERVATION_NOT_HELD, RESERVATION_EXPIRED, NOT_OWNER -> HttpStatus.CONFLICT;
            case PAYMENT_ERROR, CONFIRM_ERROR, COMPENSATION_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(Map.of(
                "error", ex.getCode().name(),
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generic(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}

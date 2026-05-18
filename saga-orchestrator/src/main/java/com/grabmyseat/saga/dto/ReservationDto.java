package com.grabmyseat.saga.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReservationDto(
        String token,
        Long eventId,
        Long zoneId,
        List<Long> seatIds,
        List<String> attendeeNames,
        String status,
        Instant expiresAt,
        BigDecimal totalPrice
) {
    public ReservationDto(String token, Long eventId, Long zoneId, List<Long> seatIds,
                          String status, Instant expiresAt, BigDecimal totalPrice) {
        this(token, eventId, zoneId, seatIds, List.of("Guest"), status, expiresAt, totalPrice);
    }
}

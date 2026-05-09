package com.grabmyseat.inventory.dto;

import com.grabmyseat.inventory.model.Reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReservationResponse(
        String token,
        Long eventId,
        Long zoneId,
        List<Long> seatIds,
        List<String> attendeeNames,
        String status,
        Instant expiresAt,
        BigDecimal totalPrice
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getToken(),
                reservation.getEventId(),
                reservation.getZoneId(),
                reservation.getSeatIds(),
                reservation.getAttendeeNames(),
                reservation.getStatus().name(),
                reservation.getExpiresAt(),
                reservation.getTotalPrice()
        );
    }
}

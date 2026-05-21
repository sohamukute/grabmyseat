package com.grabmyseat.ticketing.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record TicketRequest(
        @NotNull @NotEmpty String reservationToken,
        @NotNull @Positive Long eventId,
        @NotNull @Positive Long zoneId,
        @NotNull @Positive Long userId,
        @NotNull @NotEmpty List<@Positive Long> seatIds,
        @NotNull @NotEmpty List<String> attendeeNames,
        @NotNull @Positive BigDecimal price
) {
    public TicketRequest(String reservationToken, Long eventId, Long zoneId, Long userId,
                         List<Long> seatIds, BigDecimal price) {
        this(reservationToken, eventId, zoneId, userId, seatIds, List.of("Guest"), price);
    }
}

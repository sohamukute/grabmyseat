package com.grabmyseat.inventory.dto;

import com.grabmyseat.inventory.model.SaleType;

import java.time.Instant;

public record SaleAccessResponse(
        Long eventId,
        SaleType saleType,
        Instant queueOpensAt,
        Instant saleStartsAt,
        Instant saleEndsAt,
        long availableSeats,
        boolean canJoinQueue,
        boolean canExpressInterest,
        String status
) {
}

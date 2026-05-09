package com.grabmyseat.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreateZoneRequest(
        @NotBlank String name,
        @NotNull @Min(1) Integer capacity,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @Valid List<CreateSeatRequest> seats,
        String type
) {
    public CreateZoneRequest(String name, Integer capacity, BigDecimal price, List<CreateSeatRequest> seats) {
        this(name, capacity, price, seats, null);
    }
}

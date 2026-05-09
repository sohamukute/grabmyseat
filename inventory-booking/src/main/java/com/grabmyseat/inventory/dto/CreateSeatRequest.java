package com.grabmyseat.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSeatRequest(
        @NotBlank String rowLabel,
        @NotNull @Min(1) Integer number
) {
}

package com.grabmyseat.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReserveQuantityRequest(
        @NotNull Long eventId,
        @NotNull Long zoneId,
        @Min(1) @Max(4) int quantity,
        @Size(max = 4) List<@Size(min = 2, max = 120) String> attendeeNames
) {
}

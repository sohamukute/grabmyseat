package com.grabmyseat.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReserveSeatsRequest(
        @NotNull Long eventId,
        @NotNull Long zoneId,
        @NotEmpty @Size(min = 1, max = 4) List<@NotNull @Min(1) Long> seatIds,
        @Size(max = 4) List<@Size(min = 2, max = 120) String> attendeeNames
) {
    public ReserveSeatsRequest(Long eventId, Long zoneId, List<Long> seatIds) {
        this(eventId, zoneId, seatIds, null);
    }
}

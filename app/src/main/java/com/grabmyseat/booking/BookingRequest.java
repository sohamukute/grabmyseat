package com.grabmyseat.booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BookingRequest(
        @NotNull UUID requestId,
        @NotNull Long eventId,
        @NotNull Long areaId,
        @Min(1) int groupSize,
        boolean splitOk,
        @NotEmpty List<@NotBlank String> attendeeNames,
        List<@NotNull Long> seatIds) {
}

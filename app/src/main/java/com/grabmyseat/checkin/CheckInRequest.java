package com.grabmyseat.checkin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CheckInRequest(@NotNull Long eventId, @NotBlank String code) {
}

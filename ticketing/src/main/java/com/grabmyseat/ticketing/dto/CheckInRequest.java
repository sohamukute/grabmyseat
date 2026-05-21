package com.grabmyseat.ticketing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CheckInRequest(@NotNull @Size(min = 0, max = 4) List<String> attendeesPresent) { }

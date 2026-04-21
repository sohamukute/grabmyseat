package com.grabmyseat.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record CreateEventRequest(
        @NotNull Long venueId,
        @NotBlank @Size(max = 150) String title,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull Instant bookingOpensAt,
        @NotNull Instant bookingClosesAt,
        @NotNull @Min(1) Integer maxPerPerson,
        @NotBlank @Size(max = 30) String category,
        @NotBlank @Size(max = 200) String tagline,
        @NotBlank @Size(max = 4000) String description,
        @NotBlank @Size(max = 100) String presentedBy,
        @NotBlank @Size(max = 60) String language,
        @NotBlank @Size(max = 30) String ageGuidance,
        @Size(max = 20) List<@Valid Performer> performers) {
}

package com.grabmyseat.inventory.dto;

import jakarta.validation.Valid;
import com.grabmyseat.inventory.model.SaleType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record CreateEventRequest(
        @NotBlank String name,
        @NotBlank String venue,
        @NotBlank
        @Size(max = 2048)
        @Pattern(
                regexp = "^(https?://\\S+|/api/inventory/posters/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp))$",
                message = "must be an http or https image URL or an uploaded poster URL") String artworkUrl,
        @NotNull @Future Instant startsAt,
        Instant endsAt,
        Instant queueOpensAt,
        @NotNull @FutureOrPresent Instant saleStartsAt,
        @NotNull Instant saleEndsAt,
        @NotNull SaleType saleType,
        @Valid EventLayoutRequest layout,
        @Valid List<CreateZoneRequest> zones
) {
    public CreateEventRequest(String name, String venue, String artworkUrl, Instant startsAt, Instant endsAt,
                              Instant saleStartsAt, Instant saleEndsAt, SaleType saleType,
                              @Valid List<CreateZoneRequest> zones) {
        this(name, venue, artworkUrl, startsAt, endsAt, null, saleStartsAt, saleEndsAt, saleType, null, zones);
    }

}

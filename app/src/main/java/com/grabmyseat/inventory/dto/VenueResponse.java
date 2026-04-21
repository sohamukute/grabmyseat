package com.grabmyseat.inventory.dto;

import com.grabmyseat.inventory.model.Venue;

public record VenueResponse(Long id, String name, String city) {

    public static VenueResponse from(Venue venue) {
        return new VenueResponse(venue.getId(), venue.getName(), venue.getCity());
    }
}

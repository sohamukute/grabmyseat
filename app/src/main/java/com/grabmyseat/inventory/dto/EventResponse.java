package com.grabmyseat.inventory.dto;

import com.grabmyseat.inventory.model.Event;
import com.grabmyseat.inventory.model.EventStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record EventResponse(
        Long id,
        String title,
        Long venueId,
        String venueName,
        String city,
        Instant startsAt,
        Instant endsAt,
        Instant bookingOpensAt,
        Instant bookingClosesAt,
        int maxPerPerson,
        EventStatus status,
        String category,
        String tagline,
        String description,
        String presentedBy,
        String language,
        String ageGuidance,
        long durationMinutes,
        List<Performer> performers,
        boolean waitingRoom) {

    public static EventResponse from(Event event) {
        return from(event, List.of());
    }

    public static EventResponse from(Event event, List<Performer> performers) {
        return new EventResponse(event.getId(), event.getTitle(), event.getVenue().getId(),
                event.getVenue().getName(), event.getVenue().getCity(), event.getStartsAt(), event.getEndsAt(),
                event.getBookingOpensAt(), event.getBookingClosesAt(), event.getMaxPerPerson(), event.getStatus(),
                event.getCategory(), event.getTagline(), event.getDescription(), event.getPresentedBy(),
                event.getLanguage(), event.getAgeGuidance(),
                Duration.between(event.getStartsAt(), event.getEndsAt()).toMinutes(), performers, event.isWaitingRoom());
    }
}

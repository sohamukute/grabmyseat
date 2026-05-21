package com.grabmyseat.ticketing.dto;

import com.grabmyseat.ticketing.model.Ticket;

import java.time.Instant;
import java.util.List;

public record TicketResponse(
        Long id,
        String reservationToken,
        Long eventId,
        Long zoneId,
        Long userId,
        List<Long> seatIds,
        String holderName,
        List<String> attendeeNames,
        java.util.Map<String, String> attendance,
        String qrPayload,
        Instant createdAt,
        Instant usedAt
) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getReservationToken(),
                ticket.getEventId(),
                ticket.getZoneId(),
                ticket.getUserId(),
                ticket.getSeatIds(),
                ticket.getHolderName(),
                ticket.getAttendeeNames(),
                ticket.getAttendance().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey, entry -> entry.getValue().name(), (left, right) -> left, java.util.LinkedHashMap::new)),
                ticket.getQrPayload(),
                ticket.getCreatedAt(),
                ticket.getUsedAt()
        );
    }
}

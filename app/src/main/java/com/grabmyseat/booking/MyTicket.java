package com.grabmyseat.booking;

import java.time.OffsetDateTime;

public record MyTicket(long ticketId, long bookingId, String code, long eventId, String eventTitle,
                       OffsetDateTime startsAt, String areaName, String rowLabel, int number,
                       String attendeeName, String status) {
}

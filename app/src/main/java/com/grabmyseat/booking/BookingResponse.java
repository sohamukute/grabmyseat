package com.grabmyseat.booking;

import java.time.Instant;
import java.util.List;

public record BookingResponse(long bookingId, List<BookedSeat> seats, long amountPaise, String payStatus, Instant heldUntil) {

    public record BookedSeat(long ticketId, String rowLabel, int number, String attendeeName) {
    }
}

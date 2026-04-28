package com.grabmyseat.booking;

public record QueueResponse(long id, long eventId, long areaId, int groupSize, boolean splitOk,
                            QueueStatus status, Long bookingId, long position) {
}

package com.grabmyseat.inventory.dto;

import java.util.List;

public record EventReport(List<AreaLine> areas, List<Attendee> attendees) {

    public record AreaLine(long areaId, String name, long total, long booked, long queueLength, long checkedIn) {
    }

    public record Attendee(String attendeeName, String areaName, String rowLabel, int number, String status,
                           String bookedBy) {
    }
}

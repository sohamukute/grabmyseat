package com.grabmyseat.inventory.dto;

import com.grabmyseat.inventory.model.Attendee;

public record AttendeeResponse(Long id, String name, Integer age, String mobile, String email) {

    public static AttendeeResponse from(Attendee attendee) {
        return new AttendeeResponse(attendee.getId(), attendee.getName(), attendee.getAge(),
                attendee.getMobile(), attendee.getEmail());
    }
}

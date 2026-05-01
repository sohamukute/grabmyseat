package com.grabmyseat.auth.dto;

public record OrganizerRegistrationResponse(
        String username,
        String companyName,
        TokenResponse session) {
}

package com.grabmyseat.waitingroom.dto;

import com.grabmyseat.waitingroom.model.OfferStatus;

import java.time.Instant;

public record WaitlistStatusResponse(String token, OfferStatus status, Instant offerExpiresAt, String permitToken) {}

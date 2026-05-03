package com.grabmyseat.waitingroom.dto;

public record JoinQueueResponse(String token, Long position, Long estimatedWaitSeconds) {
}

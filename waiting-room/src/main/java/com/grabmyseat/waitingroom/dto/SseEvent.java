package com.grabmyseat.waitingroom.dto;

public record SseEvent(String event, Long position, String permitToken) {
}

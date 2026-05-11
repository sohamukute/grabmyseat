package com.grabmyseat.inventory.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class WaitingRoomClient {

    private static final Logger log = LoggerFactory.getLogger(WaitingRoomClient.class);

    private final RestTemplate restTemplate;
    private final String baseUri;
    private final String internalApiKey;

    public WaitingRoomClient(RestTemplate restTemplate,
                             @Value("${waiting-room.uri:[REDACTED-URL]") String baseUri,
                             @Value("${waiting-room.internal.api-key:}") String internalApiKey) {
        this.restTemplate = restTemplate;
        this.baseUri = baseUri;
        this.internalApiKey = internalApiKey;
    }

    @CircuitBreaker(name = "waiting-room", fallbackMethod = "notifyReleaseFallback")
    public void notifyRelease(Long eventId, Long zoneId, int count) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Api-Key", internalApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Integer>> request = new HttpEntity<>(Map.of("count", count), headers);
        restTemplate.postForEntity(
                baseUri + "/api/waiting-room/internal/events/{eventId}/zones/{zoneId}/release",
                request,
                Void.class,
                eventId, zoneId);
    }

    private void notifyReleaseFallback(Long eventId, Long zoneId, int count, Throwable ex) {
        log.warn("waiting-room notifyRelease fallback for event={} zone={} count={}: {}",
                eventId, zoneId, count, ex.getMessage());
    }
}

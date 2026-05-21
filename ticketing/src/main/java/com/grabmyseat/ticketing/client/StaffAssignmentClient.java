package com.grabmyseat.ticketing.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Component
public class StaffAssignmentClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalApiKey;
    private final Cache<AssignmentKey, Boolean> cache;

    public StaffAssignmentClient(RestTemplate restTemplate,
                                 @Value("${inventory-booking.uri:http://localhost:8082}") String baseUrl,
                                 @Value("${inventory-booking.internal.api-key:}") String internalApiKey,
                                 @Value("${staff-assignment.cache.ttl-seconds:30}") long cacheTtlSeconds,
                                 @Value("${staff-assignment.cache.max-size:10000}") long cacheMaxSize) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.internalApiKey = internalApiKey;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(cacheTtlSeconds))
                .maximumSize(cacheMaxSize)
                .build();
    }

    public boolean hasActiveAssignment(Long eventId, Long userId) {
        return fetch(eventId, userId).active;
    }

    public boolean isAuthorizedScanner(Long eventId, Long userId) {
        return cache.get(new AssignmentKey(eventId, userId), key -> {
            ScanAuthorization auth = fetch(key.eventId(), key.userId());
            return auth.active || auth.organizer;
        });
    }

    private ScanAuthorization fetch(Long eventId, Long userId) {
        HttpEntity<Void> entity = new HttpEntity<>(internalHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/inventory/internal/events/{eventId}/staff/{userId}",
                HttpMethod.GET,
                entity,
                Map.class,
                eventId,
                userId);
        Map body = response.getBody();
        if (body == null) {
            return new ScanAuthorization(false, false);
        }
        boolean active = Boolean.TRUE.equals(body.get("active"));
        boolean organizer = Boolean.TRUE.equals(body.get("organizer"));
        return new ScanAuthorization(active, organizer);
    }

    private record ScanAuthorization(boolean active, boolean organizer) {
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Api-Key", internalApiKey);
        return headers;
    }

    private record AssignmentKey(Long eventId, Long userId) {
    }
}

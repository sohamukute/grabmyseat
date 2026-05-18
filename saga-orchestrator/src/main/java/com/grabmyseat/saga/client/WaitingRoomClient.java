package com.grabmyseat.saga.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Component
public class WaitingRoomClient {

    private static final Logger log = LoggerFactory.getLogger(WaitingRoomClient.class);

    private final WebClient webClient;

    public WaitingRoomClient(WebClient.Builder builder,
                             @Value("${waiting-room.uri:[REDACTED-URL]") String baseUri,
                             @Value("${waiting-room.internal.api-key:}") String internalApiKey) {
        this.webClient = builder
                .baseUrl(baseUri)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }

    @CircuitBreaker(name = "waiting-room", fallbackMethod = "notifyReleaseFallback")
    public void notifyRelease(Long eventId, Long zoneId, int count) {
        webClient.post()
                .uri("/api/waiting-room/internal/events/{eventId}/zones/{zoneId}/release", eventId, zoneId)
                .bodyValue(Map.of("count", count))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .map(body -> new RuntimeException("waiting-room returned error: " + body)))
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(5))
                .block();
    }

    private void notifyReleaseFallback(Long eventId, Long zoneId, int count, Throwable ex) {
        log.warn("waiting-room notifyRelease fallback for event={} zone={} count={}: {}",
                eventId, zoneId, count, ex.getMessage());
    }
}

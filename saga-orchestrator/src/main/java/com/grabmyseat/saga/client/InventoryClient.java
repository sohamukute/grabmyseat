package com.grabmyseat.saga.client;

import com.grabmyseat.saga.dto.EventDto;
import com.grabmyseat.saga.dto.ReservationDto;
import com.grabmyseat.saga.dto.ZonePriceResponse;
import com.grabmyseat.saga.service.SagaException;

import java.math.BigDecimal;
import java.time.Duration;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Component
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private final WebClient webClient;

    public InventoryClient(WebClient.Builder builder,
                           @Value("${inventory.uri}") String baseUri) {
        this.webClient = builder.baseUrl(baseUri).build();
    }

    @CircuitBreaker(name = "inventory", fallbackMethod = "getReservationFallback")
    public ReservationDto getReservation(String token) {
        return webClient.get()
                .uri("/api/inventory/reservations/{token}", token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .map(body -> new ClientException(resp.statusCode(), body)))
                .bodyToMono(ReservationDto.class)
                .timeout(Duration.ofSeconds(5))
                .block();
    }

    private ReservationDto getReservationFallback(String token, Throwable ex) {
        log.warn("inventory getReservation fallback for token={}: {}", token, ex.getMessage());
        if (ex instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException(ex);
    }

    @CircuitBreaker(name = "inventory", fallbackMethod = "getEventFallback")
    public EventDto getEvent(Long eventId) {
        return webClient.get()
                .uri("/api/inventory/events/{id}", eventId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .map(body -> new ClientException(resp.statusCode(), body)))
                .bodyToMono(EventDto.class)
                .timeout(Duration.ofSeconds(5))
                .block();
    }

    private EventDto getEventFallback(Long eventId, Throwable ex) {
        log.warn("inventory getEvent fallback for eventId={}: {}", eventId, ex.getMessage());
        if (ex instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException(ex);
    }

    @CircuitBreaker(name = "inventory", fallbackMethod = "getZonePriceFallback")
    public BigDecimal getZonePrice(Long eventId, Long zoneId) {
        ZonePriceResponse response = webClient.get()
                .uri("/api/inventory/events/{eventId}/zones/{zoneId}", eventId, zoneId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .map(body -> new ClientException(resp.statusCode(), body)))
                .bodyToMono(ZonePriceResponse.class)
                .timeout(Duration.ofSeconds(5))
                .block();
        return response != null ? response.price() : null;
    }

    private BigDecimal getZonePriceFallback(Long eventId, Long zoneId, Throwable ex) {
        log.warn("inventory getZonePrice fallback for eventId={} zoneId={}: {}", eventId, zoneId, ex.getMessage());
        if (ex instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException(ex);
    }

    @CircuitBreaker(name = "inventory", fallbackMethod = "confirmReservationFallback")
    public void confirmReservation(String token, Long userId) {
        webClient.post()
                .uri("/api/inventory/reservations/{token}/confirm", token)
                .header("X-User-Id", String.valueOf(userId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .map(body -> new ClientException(resp.statusCode(), body)))
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(5))
                .block();
    }

    private void confirmReservationFallback(String token, Long userId, Throwable ex) {
        log.warn("inventory confirmReservation fallback for token={} userId={}: {}", token, userId, ex.getMessage());
        if (ex instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException(ex);
    }

    @CircuitBreaker(name = "inventory", fallbackMethod = "cancelReservationFallback")
    public void cancelReservation(String token, Long userId) {
        webClient.post()
                .uri("/api/inventory/reservations/{token}/cancel", token)
                .header("X-User-Id", String.valueOf(userId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .map(body -> new ClientException(resp.statusCode(), body)))
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(5))
                .block();
    }

    private void cancelReservationFallback(String token, Long userId, Throwable ex) {
        log.warn("inventory cancelReservation fallback for token={} userId={}: {}", token, userId, ex.getMessage());
        if (ex instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException(ex);
    }
}

package com.grabmyseat.saga.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class TicketingClient {

    private static final Logger log = LoggerFactory.getLogger(TicketingClient.class);

    private final WebClient webClient;
    private final String internalApiKey;

    public TicketingClient(WebClient.Builder builder,
                           @Value("${ticketing.uri:[REDACTED-URL]}") String baseUri,
                           @Value("${ticketing.internal.api-key:}") String internalApiKey) {
        this.webClient = builder.baseUrl(baseUri).build();
        this.internalApiKey = internalApiKey;
    }

    public void issueTicket(Long userId, String reservationToken, Long eventId, Long zoneId,
                            List<Long> seatIds, List<String> attendeeNames, BigDecimal price) {
        try {
            webClient.post()
                    .uri("/api/ticketing/internal/tickets")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .header("X-User-Id", String.valueOf(userId))
                    .body(BodyInserters.fromValue(Map.of(
                            "reservationToken", reservationToken,
                            "eventId", eventId,
                            "zoneId", zoneId,
                            "userId", userId,
                            "seatIds", seatIds,
                            "attendeeNames", attendeeNames == null ? List.of() : attendeeNames,
                            "price", price.toPlainString())))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                            .map(body -> new ClientException(resp.statusCode(), body)))
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(5))
                    .block();
        } catch (Exception ex) {
            log.warn("failed to issue ticket for reservation {}: {}", reservationToken, ex.getMessage());
        }
    }
}

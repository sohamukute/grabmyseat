package com.grabmyseat.saga.client;

import com.grabmyseat.saga.dto.LedgerEntryDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import reactor.core.publisher.Mono;

@Component
public class WalletClient {

    private static final Logger log = LoggerFactory.getLogger(WalletClient.class);

    private final WebClient webClient;

    public WalletClient(WebClient.Builder builder,
                        @Value("${wallet.uri}") String baseUri,
                        @Value("${wallet.internal.api-key}") String apiKey) {
        this.webClient = builder.baseUrl(baseUri)
                .defaultHeader("X-Internal-Api-Key", apiKey)
                .build();
    }

    @CircuitBreaker(name = "wallet", fallbackMethod = "debitFallback")
    public LedgerEntryDto debit(Long userId, BigDecimal amount, String idempotencyKey, String reference) {
        return webClient.post()
                .uri("/api/wallet/internal/debit")
                .body(BodyInserters.fromValue(Map.of(
                        "userId", userId,
                        "amount", amount.toPlainString(),
                        "idempotencyKey", idempotencyKey,
                        "reference", reference)))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .map(body -> new ClientException(resp.statusCode(), body)))
                .bodyToMono(LedgerEntryDto.class)
                .timeout(Duration.ofSeconds(5))
                .block();
    }

    private LedgerEntryDto debitFallback(Long userId, BigDecimal amount, String idempotencyKey, String reference, Throwable ex) {
        log.warn("wallet debit fallback for userId={} amount={}: {}", userId, amount, ex.getMessage());
        if (ex instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException(ex);
    }

    @CircuitBreaker(name = "wallet", fallbackMethod = "creditFallback")
    public LedgerEntryDto credit(Long userId, BigDecimal amount, String idempotencyKey, String reference) {
        return webClient.post()
                .uri("/api/wallet/internal/credit")
                .body(BodyInserters.fromValue(Map.of(
                        "userId", userId,
                        "amount", amount.toPlainString(),
                        "idempotencyKey", idempotencyKey,
                        "reference", reference)))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .map(body -> new ClientException(resp.statusCode(), body)))
                .bodyToMono(LedgerEntryDto.class)
                .timeout(Duration.ofSeconds(5))
                .block();
    }

    private LedgerEntryDto creditFallback(Long userId, BigDecimal amount, String idempotencyKey, String reference, Throwable ex) {
        log.warn("wallet credit fallback for userId={} amount={}: {}", userId, amount, ex.getMessage());
        if (ex instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException(ex);
    }
}

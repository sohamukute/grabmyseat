package com.grabmyseat.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=test-secret-test-secret-test-secret-0123456789",
                "management.health.redis.enabled=false"
        })
@AutoConfigureWebTestClient
class GatewayAuthFilterTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-0123456789";

    @Autowired
    WebTestClient client;

    @Test
    void health_is_open_without_a_token() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void protected_route_without_token_is_rejected() {
        client.get().uri("/api/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void sale_access_is_not_public() {
        client.get().uri("/api/inventory/events/1/sale-access")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void poster_download_is_public() {
        client.get().uri("/api/inventory/posters/0f6ca1df-3e7a-472f-99ab-1702be96d9ca.png")
                .exchange()
                .expectStatus().value(status -> assertNotEquals(401, status));
    }

    @Test
    void poster_upload_is_not_public() {
        client.post().uri("/api/inventory/posters")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.code").isEqualTo("unauthorized")
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.fieldErrors").isMap();
    }

    @Test
    void garbage_token_is_rejected() {
        client.get().uri("/api/auth/me")
                .header("Authorization", "Bearer not.a.real.token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void refresh_token_cannot_open_a_protected_route() {
        client.get().uri("/api/auth/me")
                .header("Authorization", "Bearer " + refreshToken())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private String refreshToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("mallory")
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();
    }
}

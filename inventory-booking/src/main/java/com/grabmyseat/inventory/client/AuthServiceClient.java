package com.grabmyseat.inventory.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class AuthServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalApiKey;

    public AuthServiceClient(RestTemplate restTemplate,
                             @Value("${auth-service.uri:http://localhost:8081}") String baseUrl,
                             @Value("${auth-service.internal.api-key:}") String internalApiKey) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.internalApiKey = internalApiKey;
    }

    public LookupResult lookupByUsername(String username) {
        HttpEntity<Void> entity = new HttpEntity<>(internalHeaders());
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/api/auth/internal/users/{username}",
                    HttpMethod.GET,
                    entity,
                    Map.class,
                    username);
            Map body = response.getBody();
            if (body == null) {
                throw new RuntimeException("auth-service returned empty body for username lookup");
            }
            Number userId = (Number) body.get("userId");
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) body.get("roles");
            return new LookupResult(userId.longValue(), (String) body.get("username"), roles);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        }
    }

    public DisplayUser lookupDisplayById(Long userId) {
        HttpEntity<Void> entity = new HttpEntity<>(internalHeaders());
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/api/auth/internal/users/id/{userId}",
                    HttpMethod.GET,
                    entity,
                    Map.class,
                    userId);
            Map body = response.getBody();
            if (body == null) {
                throw new RuntimeException("auth-service returned empty body for user display lookup");
            }
            Number responseUserId = (Number) body.get("userId");
            return new DisplayUser(responseUserId.longValue(), (String) body.get("username"));
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        }
    }

    public void grantRole(Long userId, String role) {
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                Map.of("role", role),
                internalHeaders());
        restTemplate.exchange(
                baseUrl + "/api/auth/internal/users/{userId}/roles",
                HttpMethod.POST,
                entity,
                Void.class,
                userId);
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Api-Key", internalApiKey);
        return headers;
    }

    public record LookupResult(Long userId, String username, List<String> roles) {
    }

    public record DisplayUser(Long userId, String username) {
    }
}

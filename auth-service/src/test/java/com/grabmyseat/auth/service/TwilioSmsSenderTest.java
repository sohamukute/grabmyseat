package com.grabmyseat.auth.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwilioSmsSenderTest {
    private HttpServer server;
    private URI endpoint;
    private final Map<String, String> request = new ConcurrentHashMap<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/Messages.json", exchange -> {
            request.put("authorization", exchange.getRequestHeaders().getFirst("Authorization"));
            request.put("body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(201, 0);
            exchange.getResponseBody().close();
        });
        server.start();
        endpoint = URI.create("http://localhost:" + server.getAddress().getPort() + "/Messages.json");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsAnAuthenticatedFormRequestToTwilio() {
        new TwilioSmsSender(HttpClient.newHttpClient(), endpoint, "AC123", "token123", "+15550001111")
                .send("+917666556115", "GrabMySeat verification code: 123456");

        assertEquals("Basic " + Base64.getEncoder().encodeToString("AC123:token123".getBytes(StandardCharsets.UTF_8)), request.get("authorization"));
        assertTrue(request.get("body").contains("To=%2B917666556115"));
        assertTrue(request.get("body").contains("From=%2B15550001111"));
        assertTrue(request.get("body").contains("Body=GrabMySeat+verification+code%3A+123456"));
    }
    @Test
    void stripsFormattingWhitespaceFromSenderNumber() {
        new TwilioSmsSender(HttpClient.newHttpClient(), endpoint, "AC123", "token123", "+1 555 000 1111")
                .send("+917666556115", "GrabMySeat verification code: 123456");

        assertTrue(request.get("body").contains("From=%2B15550001111"));
    }

}

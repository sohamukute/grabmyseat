package com.grabmyseat.auth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Service
public class TwilioSmsSender {
    private final HttpClient client;
    private final URI endpoint;
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;

    @Autowired
    public TwilioSmsSender(
            @Value("${twilio.account-sid:}") String accountSid,
            @Value("${twilio.auth-token:}") String authToken,
            @Value("${twilio.from-number:}") String fromNumber) {
        this(HttpClient.newHttpClient(),
                URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json"),
                accountSid, authToken, fromNumber);
    }

    TwilioSmsSender(HttpClient client, URI endpoint, String accountSid, String authToken, String fromNumber) {
        this.client = client;
        this.endpoint = endpoint;
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber.replaceAll("\\s+", "");
    }

    public void send(String to, String body) {
        if (accountSid.isBlank() && authToken.isBlank() && fromNumber.isBlank()) return;
        if (accountSid.isBlank() || authToken.isBlank() || fromNumber.isBlank()) {
            throw new DeliveryException("Twilio SMS configuration is incomplete");
        }

        String form = "To=" + encode(to) + "&From=" + encode(fromNumber) + "&Body=" + encode(body);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        (accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DeliveryException("Twilio rejected the SMS request");
            }
        } catch (IOException exception) {
            throw new DeliveryException("Twilio SMS request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DeliveryException("Twilio SMS request interrupted", exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static final class DeliveryException extends RuntimeException {
        DeliveryException(String message) {
            super(message);
        }

        DeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

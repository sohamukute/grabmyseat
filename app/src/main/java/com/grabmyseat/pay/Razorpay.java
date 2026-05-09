package com.grabmyseat.pay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

@Component
public class Razorpay {

    private static final String API = "https://api.razorpay.com/v1";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;

    public Razorpay(ObjectMapper json, @Value("${app.razorpay.key-id}") String keyId,
                    @Value("${app.razorpay.key-secret}") String keySecret,
                    @Value("${app.razorpay.webhook-secret}") String webhookSecret) {
        this.json = json;
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
    }

    public String keyId() {
        return keyId;
    }

    public boolean webhooksOn() {
        return !webhookSecret.isBlank();
    }

    public String createOrder(long amountPaise, String receipt, long bookingId) {
        return call("POST", "/orders", Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt,
                "notes", Map.of("booking", String.valueOf(bookingId)))).path("id").asText();
    }

    public JsonNode payment(String paymentId) {
        return call("GET", "/payments/" + paymentId, null);
    }

    public JsonNode capture(String paymentId, long amountPaise) {
        return call("POST", "/payments/" + paymentId + "/capture", Map.of("amount", amountPaise, "currency", "INR"));
    }

    public JsonNode orderPayments(String orderId) {
        return call("GET", "/orders/" + orderId + "/payments", null).path("items");
    }

    public JsonNode refunds(String paymentId) {
        return call("GET", "/payments/" + paymentId + "/refunds", null).path("items");
    }

    public String refund(String paymentId, long amountPaise, long refundRow) {
        return call("POST", "/payments/" + paymentId + "/refund",
                Map.of("amount", amountPaise, "notes", Map.of("refund_row", String.valueOf(refundRow)))).path("id").asText();
    }

    public boolean checkoutSigned(String orderId, String paymentId, String signature) {
        return same(hmac(keySecret, orderId + "|" + paymentId), signature);
    }

    public boolean webhookSigned(String body, String signature) {
        return webhooksOn() && same(hmac(webhookSecret, body), signature);
    }

    private JsonNode call(String method, String path, Object body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(API + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/json");
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode node = json.readTree(response.body());
            if (response.statusCode() >= 300) {
                throw new PaymentException(node.path("error").path("description").asText("Razorpay said no"));
            }
            return node;
        } catch (IOException failed) {
            throw new PaymentException("Could not reach Razorpay");
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new PaymentException("Could not reach Razorpay");
        }
    }

    private String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private boolean same(String expected, String given) {
        return given != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }
}

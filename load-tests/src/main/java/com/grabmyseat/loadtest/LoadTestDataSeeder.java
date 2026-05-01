package com.grabmyseat.loadtest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class LoadTestDataSeeder {

    public static void main(String[] args) throws Exception {
        String baseUrl = System.getenv().getOrDefault("LOAD_TEST_BASE_URL", "[REDACTED-URL]");
        String apiKey = System.getenv().getOrDefault("INVENTORY_BOOKING_INTERNAL_API_KEY", "");
        int seatCount = Integer.parseInt(System.getenv().getOrDefault("LOAD_TEST_SEAT_COUNT", "100"));

        String uri = baseUrl + "/api/inventory/internal/load-test/seed";
        String body = "{\"seatCount\":" + seatCount + "}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Content-Type", "application/json")
                .header("X-Internal-Api-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            System.out.println("Seeded load-test data: " + response.body());
        } else {
            System.err.println("Seeding failed: HTTP " + response.statusCode() + " " + response.body());
            System.exit(1);
        }
    }
}

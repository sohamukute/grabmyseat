package com.grabmyseat.waitingroom.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

/** Keeps queue admission consistent with the sale rules owned by inventory. */
@Component
public class InventorySaleClient {

    private final RestClient client;

    public InventorySaleClient(RestClient.Builder builder,
                               @Value("${inventory.uri:http://localhost:8082}") String inventoryUri) {
        this.client = builder.baseUrl(inventoryUri).build();
    }

    public SaleAccess access(Long eventId) {
        SaleAccess access = client.get().uri("/api/inventory/events/{id}/sale-access", eventId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) ->
                        new IllegalStateException("event sale status is unavailable: " + response.getStatusCode()))
                .body(SaleAccess.class);
        if (access == null) throw new IllegalStateException("event sale status is unavailable");
        return access;
    }

    public void requireQueueAccess(Long eventId) {
        SaleAccess access = access(eventId);
        if (!access.canJoinQueue()) {
            throw new IllegalStateException(access.status().equals("SOLD_OUT_INTEREST_OPEN")
                    ? "this event is sold out; register interest instead"
                    : "queue access is not available: " + access.status());
        }
    }

    public void requireInterestAccess(Long eventId) {
        SaleAccess access = access(eventId);
        if (!access.canExpressInterest()) {
            throw new IllegalStateException("interest registration is unavailable: " + access.status());
        }
    }

    public record SaleAccess(Long eventId, String saleType, Instant queueOpensAt, Instant saleStartsAt,
                             Instant saleEndsAt, long availableSeats, boolean canJoinQueue,
                             boolean canExpressInterest, String status) { }
}

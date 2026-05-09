package com.grabmyseat.pay;

import java.time.Instant;

public record PayOrder(String orderId, String keyId, long amountPaise, Instant heldUntil, String email) {
}

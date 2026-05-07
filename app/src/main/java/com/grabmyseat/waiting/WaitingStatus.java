package com.grabmyseat.waiting;

import java.time.Instant;

public record WaitingStatus(String state, Integer position, long ahead, Instant passExpiresAt,
                            Instant roomOpensAt, Instant saleOpensAt) {
}

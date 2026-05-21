package com.grabmyseat.ticketing.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class TicketSigningHealthIndicator implements HealthIndicator {

    private static final int MIN_KEY_LENGTH_BYTES = 32;

    private final String signingKey;

    public TicketSigningHealthIndicator(@Value("${ticket.signing-key:}") String signingKey) {
        this.signingKey = signingKey;
    }

    @Override
    public Health health() {
        if (signingKey == null || signingKey.isBlank()) {
            return Health.down().withDetail("error", "ticket signing key is missing").build();
        }
        if (signingKey.getBytes().length < MIN_KEY_LENGTH_BYTES) {
            return Health.down()
                    .withDetail("error", "ticket signing key is too short (min " + MIN_KEY_LENGTH_BYTES + " bytes)")
                    .build();
        }
        return Health.up()
                .withDetail("keyLengthBytes", signingKey.getBytes().length)
                .build();
    }
}

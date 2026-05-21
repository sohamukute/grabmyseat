package com.grabmyseat.ticketing.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

class TicketSigningHealthIndicatorTest {

    @Test
    void health_upWhenKeyIsLongEnough() {
        TicketSigningHealthIndicator indicator = new TicketSigningHealthIndicator(
                "this-key-is-definitely-longer-than-thirty-two-bytes");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("keyLengthBytes");
    }

    @Test
    void health_downWhenKeyIsMissing() {
        TicketSigningHealthIndicator indicator = new TicketSigningHealthIndicator("");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", "ticket signing key is missing");
    }

    @Test
    void health_downWhenKeyIsTooShort() {
        TicketSigningHealthIndicator indicator = new TicketSigningHealthIndicator("short");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat((String) health.getDetails().get("error")).contains("too short");
    }
}

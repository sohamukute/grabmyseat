package com.grabmyseat.inventory.dto;

import com.grabmyseat.inventory.model.SaleType;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CreateEventRequestTest {

    @Test
    void acceptsGeneratedPosterUrl() {
        Instant eventStarts = Instant.now().plusSeconds(86_400);
        CreateEventRequest request = new CreateEventRequest(
                "Concert", "Arena",
                "/api/inventory/posters/123e4567-e89b-12d3-a456-426614174000.png",
                eventStarts, eventStarts.plusSeconds(7200), null,
                Instant.now().plusSeconds(60), eventStarts.minusSeconds(60), SaleType.STANDARD,
                new EventLayoutRequest(100, BigDecimal.TEN, 10, BigDecimal.TEN, 10, BigDecimal.TEN), null);

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(request))
                    .noneMatch(violation -> violation.getPropertyPath().toString().equals("artworkUrl"));
        }
    }
}

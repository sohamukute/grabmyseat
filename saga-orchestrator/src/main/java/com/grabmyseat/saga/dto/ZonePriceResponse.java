package com.grabmyseat.saga.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZonePriceResponse(BigDecimal price) {
}

package com.grabmyseat.saga.dto;

import java.math.BigDecimal;

public record ZoneDto(
        Long id,
        String name,
        Integer capacity,
        BigDecimal price
) {}

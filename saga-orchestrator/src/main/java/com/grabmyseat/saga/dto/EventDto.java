package com.grabmyseat.saga.dto;

import java.util.List;

public record EventDto(
        Long id,
        String name,
        String venue,
        List<ZoneDto> zones
) {}

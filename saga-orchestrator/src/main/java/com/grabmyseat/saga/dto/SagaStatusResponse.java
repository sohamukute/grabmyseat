package com.grabmyseat.saga.dto;

import com.grabmyseat.saga.model.SagaStatus;

public record SagaStatusResponse(
        String reservationToken,
        SagaStatus status,
        String latestStep
) {}

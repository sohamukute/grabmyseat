package com.grabmyseat.saga.model;

public enum SagaStatus {
    STARTED,
    DEBITED,
    CONFIRMED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}

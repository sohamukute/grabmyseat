package com.grabmyseat.saga.service;

public class SagaException extends RuntimeException {

    public enum Code {
        RESERVATION_NOT_FOUND,
        RESERVATION_NOT_HELD,
        RESERVATION_EXPIRED,
        NOT_OWNER,
        INSUFFICIENT_FUNDS,
        PAYMENT_ERROR,
        CONFIRM_ERROR,
        COMPENSATION_ERROR
    }

    private final Code code;

    public SagaException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}

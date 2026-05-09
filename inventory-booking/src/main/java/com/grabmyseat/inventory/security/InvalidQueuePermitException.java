package com.grabmyseat.inventory.security;

public class InvalidQueuePermitException extends RuntimeException {
    public InvalidQueuePermitException(String message) {
        super(message);
    }
}

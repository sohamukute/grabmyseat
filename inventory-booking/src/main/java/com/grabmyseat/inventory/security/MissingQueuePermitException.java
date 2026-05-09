package com.grabmyseat.inventory.security;

public class MissingQueuePermitException extends RuntimeException {
    public MissingQueuePermitException(String message) {
        super(message);
    }
}

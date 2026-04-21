package com.grabmyseat.inventory.service;

import java.util.Map;

public class EventValidationException extends IllegalArgumentException {

    private final Map<String, String> fieldErrors;

    public EventValidationException(Map<String, String> fieldErrors) {
        super("Invalid event request");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}

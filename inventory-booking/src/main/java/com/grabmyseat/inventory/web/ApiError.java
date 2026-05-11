package com.grabmyseat.inventory.web;

import java.util.Map;

public record ApiError(int status, String code, String message, Map<String, String> fieldErrors) {

    public ApiError {
        fieldErrors = Map.copyOf(fieldErrors);
    }
}

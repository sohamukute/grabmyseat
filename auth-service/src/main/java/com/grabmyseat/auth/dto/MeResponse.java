package com.grabmyseat.auth.dto;

import java.util.List;

public record MeResponse(
        String username,
        List<String> roles) {
}

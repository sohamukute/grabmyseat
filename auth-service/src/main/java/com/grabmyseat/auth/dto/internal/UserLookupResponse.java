package com.grabmyseat.auth.dto.internal;

import java.util.List;

public record UserLookupResponse(
        Long userId,
        String username,
        List<String> roles
) {
}

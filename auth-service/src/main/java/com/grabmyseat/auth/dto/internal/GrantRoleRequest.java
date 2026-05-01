package com.grabmyseat.auth.dto.internal;

import jakarta.validation.constraints.NotBlank;

public record GrantRoleRequest(
        @NotBlank
        String role
) {
}

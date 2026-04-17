package com.grabmyseat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetRequest(
        @NotBlank @Size(max = 100) String token,
        @NotBlank @Size(max = 72) @Pattern(regexp = RegisterRequest.STRONG, message = RegisterRequest.WEAK) String password) {
}

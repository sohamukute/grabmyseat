package com.grabmyseat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyRequest(@NotBlank @Size(max = 100) String token) {
}

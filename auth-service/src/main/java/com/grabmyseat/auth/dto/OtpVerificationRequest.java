package com.grabmyseat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerificationRequest(@NotBlank String phone, @Pattern(regexp = "\\d{6}") String code) {}

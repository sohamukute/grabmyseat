package com.grabmyseat.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(max = 72) @Pattern(regexp = RegisterRequest.STRONG, message = RegisterRequest.WEAK) String password,
        @NotBlank @Email @Size(max = 254) String email) {

    public static final String STRONG = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,}$";
    public static final String WEAK = "Use at least 8 characters with a capital letter, a number and a symbol.";
}

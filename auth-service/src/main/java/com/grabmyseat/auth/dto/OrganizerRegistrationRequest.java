package com.grabmyseat.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrganizerRegistrationRequest(
        @NotBlank @Size(min = 2, max = 120) String companyName,
        @NotBlank @Email @Size(max = 254) String companyEmail,
        @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$", message = "must be a valid 10-digit Indian mobile number") String companyPhone,
        @NotBlank
        @Size(min = 12, max = 72)
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).+$",
                message = "must contain uppercase, lowercase, number and symbol")
        String password) {
}

package com.grabmyseat.inventory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttendeeRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotNull @Min(0) @Max(120) Integer age,
        @NotBlank @Size(max = 20) String mobile,
        @NotBlank @Email String email
) {
}

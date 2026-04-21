package com.grabmyseat.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Performer(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 100) String role) {
}

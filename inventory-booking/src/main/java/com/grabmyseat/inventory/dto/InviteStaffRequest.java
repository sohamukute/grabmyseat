package com.grabmyseat.inventory.dto;

import jakarta.validation.constraints.NotBlank;

public record InviteStaffRequest(@NotBlank String username) {
}

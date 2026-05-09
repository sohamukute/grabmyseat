package com.grabmyseat.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record EventLayoutRequest(
        @NotNull @Min(1) Integer generalAdmissionCapacity,
        @NotNull @DecimalMin("0.00") BigDecimal generalAdmissionPrice,
        @NotNull @Min(1) Integer leftPremiumCapacity,
        @NotNull @DecimalMin("0.00") BigDecimal leftPremiumPrice,
        @NotNull @Min(1) Integer rightPremiumCapacity,
        @NotNull @DecimalMin("0.00") BigDecimal rightPremiumPrice
) {
}

package com.grabmyseat.wallet.dto;

import java.math.BigDecimal;

public record TopUpResponse(
        Long ledgerEntryId,
        Long userId,
        BigDecimal amount,
        BigDecimal balance
) {}

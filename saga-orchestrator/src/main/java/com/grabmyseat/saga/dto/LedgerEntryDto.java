package com.grabmyseat.saga.dto;

import java.math.BigDecimal;

public record LedgerEntryDto(
        Long id,
        Long walletAccountId,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String idempotencyKey,
        String reference
) {}

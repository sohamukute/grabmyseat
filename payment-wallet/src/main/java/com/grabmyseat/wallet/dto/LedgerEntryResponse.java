package com.grabmyseat.wallet.dto;

import com.grabmyseat.wallet.model.LedgerEntry;

import java.math.BigDecimal;

public record LedgerEntryResponse(
        Long id,
        Long walletAccountId,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String idempotencyKey,
        String reference
) {
    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getWalletAccount().getId(),
                entry.getType().name(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getIdempotencyKey(),
                entry.getReference());
    }
}

package com.grabmyseat.wallet.service;

import com.grabmyseat.wallet.model.LedgerEntry;

public class DuplicateIdempotencyKeyException extends RuntimeException {

    private final LedgerEntry existingEntry;

    public DuplicateIdempotencyKeyException(LedgerEntry existingEntry) {
        super("duplicate idempotency key for ledger entry " + existingEntry.getId());
        this.existingEntry = existingEntry;
    }

    public LedgerEntry getExistingEntry() {
        return existingEntry;
    }
}

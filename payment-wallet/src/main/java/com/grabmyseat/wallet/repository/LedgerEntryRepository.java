package com.grabmyseat.wallet.repository;

import com.grabmyseat.wallet.model.LedgerEntry;
import com.grabmyseat.wallet.model.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    Optional<LedgerEntry> findByWalletAccountIdAndIdempotencyKeyAndType(
            Long walletAccountId, String idempotencyKey, LedgerEntryType type);
}

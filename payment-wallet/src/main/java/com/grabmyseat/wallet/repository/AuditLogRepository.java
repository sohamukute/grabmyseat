package com.grabmyseat.wallet.repository;

import com.grabmyseat.wallet.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Optional<AuditLog> findFirstByWalletAccountIdOrderByIdDesc(Long walletAccountId);
}

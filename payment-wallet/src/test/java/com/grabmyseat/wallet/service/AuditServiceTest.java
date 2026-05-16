package com.grabmyseat.wallet.service;

import com.grabmyseat.wallet.model.AuditLog;
import com.grabmyseat.wallet.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void append_firstEntryUsesZeroHash() {
        when(auditLogRepository.findFirstByWalletAccountIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuditLog result = auditService.append(1L, "LEDGER_ENTRY", 100L, "TOP_UP",
                "{\"amount\":10}", Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(result.getPreviousHash()).isEqualTo("0".repeat(64));
        assertThat(result.getCurrentHash()).isNotBlank().hasSize(64);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getWalletAccountId()).isEqualTo(1L);
    }

    @Test
    void append_subsequentEntryChainsPreviousHash() {
        AuditLog previous = new AuditLog(1L, "LEDGER_ENTRY", 99L, "TOP_UP",
                "{}", "0".repeat(64), "abc123");
        when(auditLogRepository.findFirstByWalletAccountIdOrderByIdDesc(1L))
                .thenReturn(Optional.of(previous));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuditLog result = auditService.append(1L, "LEDGER_ENTRY", 100L, "DEBIT",
                "{\"amount\":5}", Instant.parse("2026-01-01T00:00:01Z"));

        assertThat(result.getPreviousHash()).isEqualTo("abc123");
        assertThat(result.getCurrentHash()).isNotEqualTo(result.getPreviousHash());
    }
}

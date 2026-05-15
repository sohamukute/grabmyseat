package com.grabmyseat.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grabmyseat.wallet.model.LedgerEntry;
import com.grabmyseat.wallet.model.LedgerEntryType;
import com.grabmyseat.wallet.model.WalletAccount;
import com.grabmyseat.wallet.repository.LedgerEntryRepository;
import com.grabmyseat.wallet.repository.WalletAccountRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class WalletService {

    private final WalletAccountRepository walletAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Counter paymentsDebitedCounter;
    private final Counter paymentsCreditedCounter;

    public WalletService(WalletAccountRepository walletAccountRepository,
                         LedgerEntryRepository ledgerEntryRepository,
                         AuditService auditService,
                         ObjectMapper objectMapper,
                         MeterRegistry meterRegistry) {
        this.walletAccountRepository = walletAccountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.paymentsDebitedCounter = meterRegistry == null ? null :
                Counter.builder("grabmyseat.payments.debited")
                        .description("Total number of payment debits")
                        .register(meterRegistry);
        this.paymentsCreditedCounter = meterRegistry == null ? null :
                Counter.builder("grabmyseat.payments.credited")
                        .description("Total number of payment credits/refunds")
                        .register(meterRegistry);
    }

    @Transactional
    public LedgerEntry topUp(long userId, BigDecimal amount, String idempotencyKey, long adminUserId) {
        validateAmount(amount);
        WalletAccount account = findOrCreateAccount(userId);
        Optional<LedgerEntry> existing = ledgerEntryRepository.findByWalletAccountIdAndIdempotencyKeyAndType(
                account.getId(), idempotencyKey, LedgerEntryType.TOP_UP);
        if (existing.isPresent()) {
            throw new DuplicateIdempotencyKeyException(existing.get());
        }
        account.setBalance(account.getBalance().add(amount));
        LedgerEntry entry = new LedgerEntry(account, LedgerEntryType.TOP_UP, amount,
                account.getBalance(), idempotencyKey, null);
        entry = saveOrDuplicate(account, entry, LedgerEntryType.TOP_UP, idempotencyKey);
        appendAudit(account, entry, "TOP_UP", Map.of("adminUserId", adminUserId));
        return entry;
    }

    @Transactional
    public LedgerEntry debit(long userId, BigDecimal amount, String idempotencyKey, String reference) {
        validateAmount(amount);
        WalletAccount account = findOrCreateAccount(userId);
        Optional<LedgerEntry> existing = ledgerEntryRepository.findByWalletAccountIdAndIdempotencyKeyAndType(
                account.getId(), idempotencyKey, LedgerEntryType.DEBIT);
        if (existing.isPresent()) {
            throw new DuplicateIdempotencyKeyException(existing.get());
        }
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("insufficient funds: balance=" + account.getBalance()
                    + ", requested=" + amount);
        }
        account.setBalance(account.getBalance().subtract(amount));
        LedgerEntry entry = new LedgerEntry(account, LedgerEntryType.DEBIT, amount,
                account.getBalance(), idempotencyKey, reference);
        entry = saveOrDuplicate(account, entry, LedgerEntryType.DEBIT, idempotencyKey);
        if (paymentsDebitedCounter != null) {
            paymentsDebitedCounter.increment();
        }
        appendAudit(account, entry, "DEBIT", Map.of("reference", reference));
        return entry;
    }

    @Transactional
    public LedgerEntry credit(long userId, BigDecimal amount, String idempotencyKey, String reference) {
        validateAmount(amount);
        WalletAccount account = findOrCreateAccount(userId);
        Optional<LedgerEntry> existing = ledgerEntryRepository.findByWalletAccountIdAndIdempotencyKeyAndType(
                account.getId(), idempotencyKey, LedgerEntryType.REFUND);
        if (existing.isPresent()) {
            throw new DuplicateIdempotencyKeyException(existing.get());
        }
        account.setBalance(account.getBalance().add(amount));
        LedgerEntry entry = new LedgerEntry(account, LedgerEntryType.REFUND, amount,
                account.getBalance(), idempotencyKey, reference);
        entry = saveOrDuplicate(account, entry, LedgerEntryType.REFUND, idempotencyKey);
        if (paymentsCreditedCounter != null) {
            paymentsCreditedCounter.increment();
        }
        appendAudit(account, entry, "REFUND", Map.of("reference", reference));
        return entry;
    }

    private LedgerEntry saveOrDuplicate(WalletAccount account, LedgerEntry entry,
                                        LedgerEntryType type, String idempotencyKey) {
        try {
            return ledgerEntryRepository.save(entry);
        } catch (DataIntegrityViolationException ex) {
            // True race: another transaction committed an entry with the same
            // (walletAccountId, idempotencyKey, type) tuple while we were between
            // the existence check and the insert. Re-fetch the winning row and
            // surface it as the same DUPLICATE_IDEMPOTENCY_KEY the non-racing
            // path returns, so callers see a uniform 409 instead of a generic
            // 500 from the fallback exception handler.
            LedgerEntry existing = ledgerEntryRepository
                    .findByWalletAccountIdAndIdempotencyKeyAndType(account.getId(), idempotencyKey, type)
                    .orElseThrow(() -> ex);
            throw new DuplicateIdempotencyKeyException(existing);
        }
    }

    @Transactional
    public BigDecimal getBalance(long userId) {
        return findOrCreateAccount(userId).getBalance();
    }

    private WalletAccount findOrCreateAccount(long userId) {
        return walletAccountRepository.findByUserId(userId)
                .orElseGet(() -> walletAccountRepository.save(new WalletAccount(userId)));
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    private void appendAudit(WalletAccount account, LedgerEntry entry, String action, Map<String, Object> extras) {
        try {
            Map<String, Object> payload = new HashMap<>(extras);
            payload.put("ledgerEntryId", entry.getId());
            payload.put("type", entry.getType().name());
            payload.put("amount", entry.getAmount().toPlainString());
            payload.put("balanceAfter", entry.getBalanceAfter().toPlainString());
            payload.put("idempotencyKey", entry.getIdempotencyKey());
            String json = objectMapper.writeValueAsString(payload);
            auditService.append(account.getId(), "LEDGER_ENTRY", entry.getId(), action, json,
                    entry.getCreatedAt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize audit payload", e);
        }
    }
}

package com.grabmyseat.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grabmyseat.wallet.model.LedgerEntry;
import com.grabmyseat.wallet.model.LedgerEntryType;
import com.grabmyseat.wallet.model.WalletAccount;
import com.grabmyseat.wallet.repository.LedgerEntryRepository;
import com.grabmyseat.wallet.repository.WalletAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletAccountRepository walletAccountRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private AuditService auditService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WalletService walletService;

    private final String key = UUID.randomUUID().toString();

    @Test
    void topUp_createsAccountAndLedgerEntry() {
        when(walletAccountRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(walletAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LedgerEntry entry = walletService.topUp(1L, new BigDecimal("50.00"), key, 99L);

        assertThat(entry.getType()).isEqualTo(LedgerEntryType.TOP_UP);
        assertThat(entry.getBalanceAfter()).isEqualByComparingTo("50.00");
        ArgumentCaptor<WalletAccount> accountCaptor = ArgumentCaptor.forClass(WalletAccount.class);
        verify(walletAccountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void topUp_duplicateIdempotencyKey_throwsDuplicateException() {
        WalletAccount account = new WalletAccount(1L);
        account.setBalance(new BigDecimal("50.00"));
        LedgerEntry existing = new LedgerEntry(account, LedgerEntryType.TOP_UP, new BigDecimal("50.00"),
                new BigDecimal("50.00"), key, null);
        when(walletAccountRepository.findByUserId(1L)).thenReturn(Optional.of(account));
        when(ledgerEntryRepository.findByWalletAccountIdAndIdempotencyKeyAndType(
                account.getId(), key, LedgerEntryType.TOP_UP)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> walletService.topUp(1L, new BigDecimal("50.00"), key, 99L))
                .isInstanceOf(DuplicateIdempotencyKeyException.class)
                .satisfies(ex -> assertThat(((DuplicateIdempotencyKeyException) ex).getExistingEntry().getId())
                        .isEqualTo(existing.getId()));
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void debit_succeedsWhenBalanceSufficient() {
        WalletAccount account = new WalletAccount(1L);
        account.setBalance(new BigDecimal("100.00"));
        when(walletAccountRepository.findByUserId(1L)).thenReturn(Optional.of(account));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LedgerEntry entry = walletService.debit(1L, new BigDecimal("30.00"), key, "resv-1");

        assertThat(entry.getType()).isEqualTo(LedgerEntryType.DEBIT);
        assertThat(entry.getBalanceAfter()).isEqualByComparingTo("70.00");
        assertThat(account.getBalance()).isEqualByComparingTo("70.00");
    }

    @Test
    void debit_failsWhenBalanceInsufficient() {
        WalletAccount account = new WalletAccount(1L);
        account.setBalance(new BigDecimal("10.00"));
        when(walletAccountRepository.findByUserId(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> walletService.debit(1L, new BigDecimal("30.00"), key, "resv-1"))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void debit_duplicateIdempotencyKey_throwsDuplicateException() {
        WalletAccount account = new WalletAccount(1L);
        account.setBalance(new BigDecimal("100.00"));
        LedgerEntry existing = new LedgerEntry(account, LedgerEntryType.DEBIT, new BigDecimal("30.00"),
                new BigDecimal("70.00"), key, "resv-1");
        when(walletAccountRepository.findByUserId(1L)).thenReturn(Optional.of(account));
        when(ledgerEntryRepository.findByWalletAccountIdAndIdempotencyKeyAndType(
                account.getId(), key, LedgerEntryType.DEBIT)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> walletService.debit(1L, new BigDecimal("30.00"), key, "resv-1"))
                .isInstanceOf(DuplicateIdempotencyKeyException.class);
    }

    @Test
    void credit_increasesBalanceAndRecordsRefund() {
        WalletAccount account = new WalletAccount(1L);
        account.setBalance(new BigDecimal("20.00"));
        when(walletAccountRepository.findByUserId(1L)).thenReturn(Optional.of(account));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LedgerEntry entry = walletService.credit(1L, new BigDecimal("15.00"), key, "resv-1");

        assertThat(entry.getType()).isEqualTo(LedgerEntryType.REFUND);
        assertThat(entry.getBalanceAfter()).isEqualByComparingTo("35.00");
    }

    @Test
    void getBalance_lazyCreatesAccount() {
        when(walletAccountRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(walletAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BigDecimal balance = walletService.getBalance(1L);

        assertThat(balance).isEqualByComparingTo(BigDecimal.ZERO);
        verify(walletAccountRepository).save(any());
    }
}

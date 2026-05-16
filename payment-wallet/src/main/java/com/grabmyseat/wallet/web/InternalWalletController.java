package com.grabmyseat.wallet.web;

import com.grabmyseat.wallet.dto.InternalCreditRequest;
import com.grabmyseat.wallet.dto.InternalDebitRequest;
import com.grabmyseat.wallet.dto.LedgerEntryResponse;
import com.grabmyseat.wallet.model.LedgerEntry;
import com.grabmyseat.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet/internal")
public class InternalWalletController {

    private final WalletService walletService;

    public InternalWalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/debit")
    public ResponseEntity<LedgerEntryResponse> debit(@Valid @RequestBody InternalDebitRequest request) {
        LedgerEntry entry = walletService.debit(request.userId(), request.amount(),
                request.idempotencyKey(), request.reference());
        return ResponseEntity.ok(LedgerEntryResponse.from(entry));
    }

    @PostMapping("/credit")
    public ResponseEntity<LedgerEntryResponse> credit(@Valid @RequestBody InternalCreditRequest request) {
        LedgerEntry entry = walletService.credit(request.userId(), request.amount(),
                request.idempotencyKey(), request.reference());
        return ResponseEntity.ok(LedgerEntryResponse.from(entry));
    }
}

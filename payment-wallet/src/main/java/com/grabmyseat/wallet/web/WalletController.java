package com.grabmyseat.wallet.web;

import com.grabmyseat.wallet.dto.BalanceResponse;
import com.grabmyseat.wallet.dto.DemoTopUpRequest;
import com.grabmyseat.wallet.dto.TopUpRequest;
import com.grabmyseat.wallet.dto.TopUpResponse;
import com.grabmyseat.wallet.model.LedgerEntry;
import com.grabmyseat.wallet.security.UserContext;
import com.grabmyseat.wallet.service.DuplicateIdempotencyKeyException;
import com.grabmyseat.wallet.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/admin/topups")
    public ResponseEntity<TopUpResponse> topUp(@Valid @RequestBody TopUpRequest request,
                                                HttpServletRequest httpRequest) {
        UserContext ctx = UserContext.fromRequest(httpRequest);
        if (!ctx.hasRole("ROLE_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            LedgerEntry entry = walletService.topUp(request.userId(), request.amount(),
                    request.idempotencyKey(), ctx.userId());
            return ResponseEntity.ok(toResponse(request, entry));
        } catch (DuplicateIdempotencyKeyException ex) {
            return ResponseEntity.ok(toResponse(request, ex.getExistingEntry()));
        }
    }

    @GetMapping("/me/balance")
    public ResponseEntity<BalanceResponse> balance(HttpServletRequest httpRequest) {
        UserContext ctx = UserContext.fromRequest(httpRequest);
        if (ctx.userId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        BigDecimal balance = walletService.getBalance(ctx.userId());
        return ResponseEntity.ok(new BalanceResponse(ctx.userId(), balance));
    }

    @PostMapping("/me/demo-topups")
    public ResponseEntity<TopUpResponse> demoTopUp(@Valid @RequestBody DemoTopUpRequest request,
                                                    HttpServletRequest httpRequest) {
        UserContext ctx = UserContext.fromRequest(httpRequest);
        if (ctx.userId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            LedgerEntry entry = walletService.credit(
                    ctx.userId(), request.amount(), request.idempotencyKey(), "demo-topup");
            return ResponseEntity.ok(toResponse(ctx.userId(), entry));
        } catch (DuplicateIdempotencyKeyException ex) {
            return ResponseEntity.ok(toResponse(ctx.userId(), ex.getExistingEntry()));
        }
    }

    private TopUpResponse toResponse(TopUpRequest request, LedgerEntry entry) {
        return toResponse(request.userId(), entry);
    }

    private TopUpResponse toResponse(Long userId, LedgerEntry entry) {
        return new TopUpResponse(
                entry.getId(), userId, entry.getAmount(), entry.getBalanceAfter());
    }
}

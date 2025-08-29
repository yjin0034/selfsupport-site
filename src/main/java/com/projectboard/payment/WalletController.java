package com.projectboard.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class WalletController {
    private final WalletService walletService;

    // TODO: Exception 처리 필요
    @PostMapping("/api/wallets")
    public CreatedWalletResponse createWallet(@RequestBody CreateWalletRequest request) {
        return walletService.createWallet(request);
    }

    @GetMapping("/api/users/{userId}/wallets")
    public FindWalletResponse findWalletByUserId(@PathVariable("userId") Long userId) {
        return walletService.findWalletByUserId(userId);
    }

    @PostMapping("/api/wallets/add-balance")
    public AddBalanceWalletResponse addBalance(@RequestBody AddBalanceWalletRequest request) {
        return walletService.addBalance(request);
    }

}

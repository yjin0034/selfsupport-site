package com.projectboard.payment.wallet;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 지갑 컨트롤러
 * - 지갑 생성, 사용자 ID로 지갑 조회, 잔액 추가 등을 담당.
 */
@RequiredArgsConstructor
@RestController
public class WalletController {
    private final WalletService walletService;

    // ===== 지갑 생성 =====
    @PostMapping("/api/wallets")
    public CreatedWalletResponse createWallet(@RequestBody CreateWalletRequest request) {
        return walletService.createWallet(request);
    }

    // ===== 사용자 ID로 지갑 조회 =====
    @GetMapping("/api/users/{userId}/wallets")
    public FindWalletResponse findWalletByUserId(@PathVariable("userId") Long userId) {
        return walletService.findWalletByWalletId(userId);
    }

    // ===== 잔액 추가 =====
    @PostMapping("/api/wallets/add-balance")
    public AddBalanceWalletResponse addBalance(@RequestBody AddBalanceWalletRequest request) {
        return walletService.addBalance(request);
    }

}

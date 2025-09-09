package com.projectboard.payment.transaction;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 트랜잭션 컨트롤러
 * - 충전 및 결제 요청을 처리하는 REST API 엔드포인트를 제공.
 */
@RequiredArgsConstructor
@RestController
public class TransactionController {
    private final TransactionService transactionService;

    // ===== 충전 API 엔드포인트 =====
    // - 요청 바디로 충전 요청을 받고, 충전 결과를 반환.
    @PostMapping("/api/balance/charge")
    public ChargeTransactionResponse charge(
            @RequestBody final ChargeTransactionRequest request
    ) {
        return transactionService.charge(request);
    }

    // ===== 결제 API 엔드포인트 =====
    // - 요청 바디로 결제 요청을 받고, 결제 결과를 반환.
    @PostMapping("/api/balance/payment")
    public PaymentTransactionResponse payment(
            @RequestBody final PaymentTransactionRequest request
    ) {
        return transactionService.payment(request);
    }
}

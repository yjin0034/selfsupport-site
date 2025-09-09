package com.projectboard.payment.transaction;

import java.math.BigDecimal;

/**
 * 결제 트랜잭션 요청 데이터
 * - 지갑 ID, 기부 ID, 결제 금액을 포함.
 */
public record PaymentTransactionRequest(
        Long walletId,
        String donationId,
        BigDecimal amount) {
}

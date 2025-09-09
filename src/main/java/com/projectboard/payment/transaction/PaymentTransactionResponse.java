package com.projectboard.payment.transaction;

import java.math.BigDecimal;

/**
 * 결제 트랜잭션 응답 데이터
 * - 지갑 ID와 업데이트된 잔액을 포함.
 */
public record PaymentTransactionResponse(Long walletId, BigDecimal balance) {
}

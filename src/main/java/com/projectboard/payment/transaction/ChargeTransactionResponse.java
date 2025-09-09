package com.projectboard.payment.transaction;

import java.math.BigDecimal;

/**
 * 충전 트랜잭션 응답 데이터
 * - 지갑 ID와 업데이트된 잔액을 포함.
 */
public record ChargeTransactionResponse(Long walletId, BigDecimal balance) {
}

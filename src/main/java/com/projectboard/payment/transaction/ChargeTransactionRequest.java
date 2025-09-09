package com.projectboard.payment.transaction;

import java.math.BigDecimal;

/**
 * 충전 트랜잭션 요청 데이터
 * - 지갑 ID, 주문 ID, 충전 금액을 포함.
 */
public record ChargeTransactionRequest(Long walletId, String orderId, BigDecimal amount) {
}

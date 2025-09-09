package com.projectboard.payment.wallet;

import java.math.BigDecimal;

/**
 * 지갑 잔액 추가 요청 DTO
 * - walletId: 지갑 ID
 * - amount: 추가할 금액
 */
public record AddBalanceWalletRequest(Long walletId, BigDecimal amount) {
}

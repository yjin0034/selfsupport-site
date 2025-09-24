package com.projectboard.payment.wallet;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AddBalanceWalletResponse
 * - 지갑 잔액 추가 응답 DTO
 * - 지갑 ID, 사용자 ID, 잔액, 생성 시각, 업데이트 시각 포함
 */
public record AddBalanceWalletResponse(
        Long walletId,
        Long userId,
        BigDecimal balance,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

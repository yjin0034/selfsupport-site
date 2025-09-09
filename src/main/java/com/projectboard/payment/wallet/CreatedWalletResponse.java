package com.projectboard.payment.wallet;

import java.math.BigDecimal;

/**
 * 생성된 지갑 응답 DTO
 * - id: 지갑 ID
 * - userId: 사용자 ID
 * - balance: 현재 잔액
 */
public record CreatedWalletResponse(
        Long id, Long userId, BigDecimal balance
) {
}

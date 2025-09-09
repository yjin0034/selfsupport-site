package com.projectboard.payment.wallet;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 지갑 조회 응답 DTO
 * - id: 지갑 ID
 * - userId: 사용자 ID
 * - balance: 현재 잔액
 * - createdAt: 생성 일시
 * - updatedAt: 수정 일시
 */
public record FindWalletResponse(
        Long id, Long userId, BigDecimal balance, LocalDateTime createdAt, LocalDateTime updatedAt
) {
}

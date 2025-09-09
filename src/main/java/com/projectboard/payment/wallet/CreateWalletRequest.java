package com.projectboard.payment.wallet;

/**
 * 지갑 생성 요청 DTO
 * - userId: 사용자 ID
 */
public record CreateWalletRequest(Long userId) {
}

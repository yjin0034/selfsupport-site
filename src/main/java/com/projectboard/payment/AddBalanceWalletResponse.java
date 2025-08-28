package com.projectboard.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AddBalanceWalletResponse(
        Long id, Long UserId, BigDecimal balance, LocalDateTime createdAt, LocalDateTime updatedAt
) {
}

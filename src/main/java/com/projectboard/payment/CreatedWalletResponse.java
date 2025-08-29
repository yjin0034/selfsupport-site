package com.projectboard.payment;

import java.math.BigDecimal;

public record CreatedWalletResponse(
        Long id, Long userId, BigDecimal balance
) {
}

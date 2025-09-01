package com.projectboard.payment.wallet;

import java.math.BigDecimal;

public record CreatedWalletResponse(
        Long id, Long userId, BigDecimal balance
) {
}

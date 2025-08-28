package com.projectboard.payment;

import java.math.BigDecimal;

public record CreatedWalletResponse(
        Long id, Long UserId, BigDecimal balance
) {
}

package com.projectboard.payment.transaction;

import java.math.BigDecimal;

public record ChargeTransactionResponse(Long walletId, BigDecimal balance) {
}

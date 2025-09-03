package com.projectboard.payment.transaction;

import java.math.BigDecimal;

public record ChargeTransactionRequest(Long walletId, String orderId, BigDecimal amount) {
}

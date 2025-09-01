package com.projectboard.payment.transaction;

import java.math.BigDecimal;

public record ChargeTransactionRequest(Long userId, String orderId, BigDecimal amount) {
}

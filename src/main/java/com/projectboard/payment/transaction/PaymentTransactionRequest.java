package com.projectboard.payment.transaction;

import java.math.BigDecimal;

public record PaymentTransactionRequest(
        Long walletId,
        String donationId,
        BigDecimal amount) {
}

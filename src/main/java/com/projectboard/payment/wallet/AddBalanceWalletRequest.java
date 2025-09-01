package com.projectboard.payment.wallet;

import java.math.BigDecimal;

public record AddBalanceWalletRequest(Long walletId, BigDecimal amount) {
}

package com.projectboard.payment;

import java.math.BigDecimal;

public record AddBalanceWalletRequest(Long walletId, BigDecimal amount) {
}

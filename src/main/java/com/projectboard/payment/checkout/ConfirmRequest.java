package com.projectboard.payment.checkout;

/**
 * Confirm API 요청에 필요한 DTO
 * - paymentKey/orderId/amount 필드만 포함
 */
public record ConfirmRequest(String paymentKey, String orderId, String amount
) {
}
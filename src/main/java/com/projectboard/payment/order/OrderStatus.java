package com.projectboard.payment.order;

/**
 * OrderStatus
 * - 주문의 상태를 나타내는 열거형.
 * - WAIT: 대기 중
 * - REQUESTED: 요청됨
 * - APPROVED: 승인됨
 * - FAIL: 실패
 * - CANCEL: 취소됨
 */
public enum OrderStatus {
    WAIT, REQUESTED, APPROVED, FAIL, CANCEL
}

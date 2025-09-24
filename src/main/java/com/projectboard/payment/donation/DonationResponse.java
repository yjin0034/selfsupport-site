package com.projectboard.payment.donation;

import com.projectboard.payment.transaction.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 후원 응답 DTO
 * - 후원 관련 정보를 클라이언트에 전달하기 위한 데이터 전송 객체.
 * - 후원 ID, 아이템 유형, 금액, 유형, 상태, 생성 시각, 결제 키 포함.
 * - Donation 엔티티로부터 변환하는 정적 팩토리 메서드 포함.
 *
 * @param donationId       후원 ID
 * @param itemType         후원 아이템 유형
 * @param donationAmount   후원 금액
 * @param donationType     후원 유형 (포인트, 직접)
 * @param donationStatus   후원 상태 (요청됨, 완료, 실패)
 * @param createdAt        후원 생성 시각
 * @param PaymentKey       결제 키 (결제 트랜잭션이 있는 경우)
 */
public record DonationResponse(
        Long donationId,
        String itemType,
        BigDecimal donationAmount,
        Donation.DonationType donationType,
        Donation.DonationStatus donationStatus,
        LocalDateTime createdAt,
        String PaymentKey
) {

    /**
     * Donation 엔티티로부터 DonationResponse 객체 생성 메서드
     * - 후원 엔티티를 받아 해당 정보를 기반으로 DonationResponse 객체를 생성.
     * - 관련 트랜잭션 정보도 함께 포함.
     *
     * @param donation 후원 엔티티
     * @return 생성된 DonationResponse 객체
     */
    public static DonationResponse from(Donation donation) {
        // 관련 트랜잭션 정보 가져오기
        Transaction tx = donation.getTransaction();

        // DonationResponse 객체 생성 및 반환
        return new DonationResponse(
                donation.getId(),
                donation.getDonationItem().name(),
                donation.getAmount(),
                donation.getDonationType(),
                donation.getDonationStatus(),
                donation.getCreatedAt(),
                tx != null ? tx.getPaymentKey() : null // 결제 트랜잭션이 있는 경우에만 결제 키 설정
        );
    }
}

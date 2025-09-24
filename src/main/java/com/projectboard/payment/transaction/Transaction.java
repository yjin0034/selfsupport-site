package com.projectboard.payment.transaction;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 트랜잭션 엔티티
 * - 충전 및 결제 트랜잭션 정보를 나타내는 엔티티 클래스.
 * - 멱등성 보장을 위한 orderId 고유 제약 조건 포함.
 * - 충전 및 결제 트랜잭션 생성 팩토리 메서드 포함.
 * - PG 결제 트랜잭션 지원.
 * - 포인트 후원 트랜잭션 지원.
 */
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Data
@Builder
@Table( // 트랜잭션 테이블, orderId에 고유 제약 조건(Unique Constraint) 설정
        name = "transaction",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_transaction_order", columnNames = "order_id"
        )
)
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)     // 기본 키, 자동 생성
    private Long id;                                        // 트랜잭션 ID (기본 키)

    @Column(nullable = false)
    private Long userId;                                    // 사용자 ID

    @Column(nullable = true)                                // null 허용. 직접 결제 시 null 가능
    private Long walletId;                                  // 지갑 ID

    @Column(nullable = false, unique = true, length = 100)  // null 금지, 고유 제약 조건, 길이 100
    private String orderId;                                 // 주문 ID (외부 결제 시스템의 고유 주문 식별자)

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;                // 트랜잭션 유형 (CHARGE, PAYMENT)

    @Column(nullable = false, precision = 19, scale = 2)    // 19자리 숫자, 소수점 이하 2자리
    private BigDecimal amount;                              // 금액

    private String description;                             // 설명

    @Column(length = 200)
    private String paymentKey;                              // 결제 키 (PG사 결제 고유 키, PG 결제 시에만 사용)

    private LocalDateTime createdAt;                        // 생성 시각
    private LocalDateTime updatedAt;                        // 수정 시각

    // createdAt, updatedAt 자동 설정
    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // updatedAt 자동 갱신
    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ===== 팩토리 메서드 =====

    /**
     * 충전 트랜잭션 생성 메서드
     * - 사용자 ID, 지갑 ID, 주문 ID, 금액을 받아 충전 트랜잭션을 생성.
     * - walletId가 반드시 필요하며, 없으면 예외 발생.
     * - 생성된 트랜잭션 객체를 반환.
     *
     * @param userId 사용자 ID
     * @param walletId 지갑 ID
     * @param orderId 주문 ID
     * @param amount 충전 금액
     * @return 생성된 트랜잭션 객체
     */
    public static Transaction createChargeTransaction(
            Long userId, Long walletId,
            String orderId, BigDecimal amount) {

        // walletId 필수 검증
        if (walletId == null) {
            throw new IllegalArgumentException("충전 트랜잭션에는 walletId가 반드시 필요합니다.");
        }

        // 충전 트랜잭션 생성
        return Transaction.builder()
                .userId(userId)
                .walletId(walletId)
                .orderId(orderId)                                   // 주문 ID
                .transactionType(TransactionType.CHARGE)            // 충전 유형
                .amount(amount)
                .description("충전")                                 // 설명
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();                                           // 빌더 패턴으로 트랜잭션 생성
    }

    /**
     * 결제 트랜잭션 생성 메서드
     * - 사용자 ID, 지갑 ID, 후원 ID, 금액을 받아 결제 트랜잭션을 생성.
     * - walletId가 반드시 필요하며, 없으면 예외 발생.
     * - 생성된 트랜잭션 객체를 반환.
     *
     * @param userId 사용자 ID
     * @param walletId 지갑 ID
     * @param donationId 후원 ID
     * @param amount 결제 금액
     * @return 생성된 트랜잭션 객체
     */
    public static Transaction createPaymentTransaction(
            Long userId, Long walletId,
            String donationId, BigDecimal amount) {

        // walletId 필수 검증
        if (walletId == null) {
            throw new IllegalArgumentException("일반 결제 트랜잭션에는 walletId가 반드시 필요합니다.");
        }

        // 결제 트랜잭션 생성
        return Transaction.builder()
                .userId(userId)
                .walletId(walletId)
                .orderId(donationId)                                // 주문 ID
                .transactionType(TransactionType.PAYMENT)           // 결제 유형
                .amount(amount)
                .description(donationId + " 결제")                   // 설명
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();                                           // 빌더 패턴으로 트랜잭션 생성

    }

    /**
     * PG 결제 트랜잭션 생성 메서드
     * - 사용자 ID, 주문 ID, 결제 금액, 결제 키를 받아 PG 결제 트랜잭션을 생성.
     * - 생성된 트랜잭션 객체를 반환.
     *
     * @param userId 사용자 ID
     * @param orderId 주문 ID
     * @param amount 결제 금액
     * @param paymentKey 결제 키
     * @return 생성된 트랜잭션 객체
     */
    public static Transaction createPgPaymentTransaction(
            Long userId, String orderId,
            BigDecimal amount, String paymentKey) {

        // PG 결제 트랜잭션 생성
        return Transaction.builder()
                .userId(userId)
                .orderId(orderId)
                .transactionType(TransactionType.PAYMENT)           // 결제 유형
                .amount(amount)
                .paymentKey(paymentKey)                             // PG 결제 키
                .description("PG 결제, paymentKey=" + paymentKey)    // 설명
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();                                           // 빌더 패턴으로 트랜잭션 생성
    }

    /**
     * 포인트 후원 트랜잭션 생성 메서드
     * - 사용자 ID, 지갑 ID, 후원 금액을 받아 포인트 후원 결제 트랜잭션을 생성.
     * - walletId가 반드시 필요하며, 없으면 예외 발생.
     * - 생성된 트랜잭션 객체를 반환.
     *
     * @param userId 사용자 ID
     * @param walletId 지갑 ID
     * @param amount 후원 금액
     * @return 생성된 트랜잭션 객체
     */
    public static Transaction createPointDonationTransaction(
            Long userId, Long walletId, BigDecimal amount) {

        // walletId 필수 검증
        if (walletId == null) {
            throw new IllegalArgumentException("포인트 후원 트랜잭션에는 walletId가 반드시 필요합니다.");
        }

        // 포인트 후원 트랜잭션 생성
        return Transaction.builder()
                .userId(userId)
                .walletId(walletId)
                .orderId("POINT-" + System.currentTimeMillis())     // 주문 ID (포인트 후원은 고유 ID 생성)
                .transactionType(TransactionType.PAYMENT)           // 결제 유형
                .amount(amount)
                .description("포인트 후원")                           // 설명
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();                                           // 빌더 패턴으로 트랜잭션 생성
    }

}

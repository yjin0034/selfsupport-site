package com.projectboard.payment.transaction;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Data
@Table( // 트랜잭션 테이블, orderId에 고유 제약 조건(Unique Constraint) 설정
        name = "transaction",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_trasaction_order", columnNames = "order_id"
        )
)
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 기본 키, 자동 생성
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long walletId;

    @Column(nullable = false, unique = true, length =  100) // null 금지, 고유 제약 조건, 길이 100
    private String orderId;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 2) // 19자리 숫자, 소수점 이하 2자리
    private BigDecimal amount;

    private String description;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
    // 충전 트랜잭션 생성 메서드
    public static Transaction createChargeTransaction(
            Long userId, Long walletId, String orderId,
            BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.userId = userId;
        transaction.walletId = walletId;
        transaction.orderId = orderId;
        transaction.transactionType = TransactionType.CHARGE;
        transaction.amount = amount;
        transaction.description = "충전";
        transaction.createdAt = LocalDateTime.now();
        transaction.updatedAt = LocalDateTime.now();

        return transaction;
    }

    // 결제 트랜잭션 생성 메서드
    public static Transaction createPaymentTransaction(
            Long userId, Long walletId, String donationId,
            BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.userId = userId;
        transaction.walletId = walletId;
        transaction.orderId = donationId;
        transaction.transactionType = TransactionType.PAYMENT;
        transaction.amount = amount;
        transaction.description = donationId + " 결제";
        transaction.createdAt = LocalDateTime.now();
        transaction.updatedAt = LocalDateTime.now();

        return transaction;
    }
}

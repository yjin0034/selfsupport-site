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
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long walletId;

    private String orderId;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    private BigDecimal amount;
    private String description;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

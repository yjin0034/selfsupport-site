package com.projectboard.payment.wallet;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private BigDecimal balance;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Wallet(Long userId) {
        this.userId = userId;
        this.balance = new BigDecimal(0);
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 충전 금액 검증 (검증, 계산, 상태 변경)
    // 도메인 규칙: 충전(양수만 허용), 최대 잔액 한도 준수
    public void charge(BigDecimal amount, BigDecimal balanceLimit) {
        // 입력값 방어적 검증: null 또는 0/음수 금지
        if (amount == null || amount.signum() <= 0 ) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
        }

        // 결과 잔액 계산
        BigDecimal candidate = this.balance.add(amount);

        // 하한 검증: 결과 잔액이 음수가 될 수 없음
        if (candidate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("충전 후 잔액이 음수가 될 수 없습니다.");
        }

        // 상한 검증: 결과 잔액이 허용 한도 초과인지 확인
        if (candidate.compareTo(balanceLimit) > 0) {
            throw new IllegalArgumentException("충전 한도를 초과합니다.");
        }

        // 상태 변경
        this.balance = candidate;
        this.updatedAt = LocalDateTime.now();
    }

    // 도메인 규칙: 결제/차감(양수만 허용), 잔액 부족 금지
    public void spend(BigDecimal amount) {
        // 입력값 방어적 검증: null 또는 0/음수 금지
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }

        // 결과 잔액 계산
        BigDecimal candidate = this.balance.subtract(amount);

        // 잔액 부족 검증
        if (candidate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }

        // 상태 변경
        this.balance = candidate;
        this.updatedAt = LocalDateTime.now();
    }
}

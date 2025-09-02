package com.projectboard.payment.wallet;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table( // 지갑 테이블, userId에 고유 제약 조건(Unique Constraint) 설정
        name = "wallet", // 테이블명 지정
        uniqueConstraints = @UniqueConstraint(name = "uk_wallet_user", columnNames = "user_id") // user_id 컬럼에 고유 제약 조건 설정
)
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false) // null 금지, 고유 제약 조건은 @Table에서 설정
    private Long userId;

    @Column(nullable = false, precision = 19, scale = 2) // 19자리 숫자, 소수점 이하 2자리
    private BigDecimal balance;

    @Version
    private Long version; // 낙관적 락용 버전 필드

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Wallet(Long userId) {
        this.userId = userId;
        this.balance = new BigDecimal(0);
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 테스트용 전체 필드 생성자
    public Wallet(Long id, Long userId, BigDecimal balance,
                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.balance = balance;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

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

    // ===== 도메인 규칙 =====
    // 충전(양수만 허용), 최대 한도 준수
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
        // updatedAt은 @PreUpdate로 관리됨
    }

    // 결제(양수만 허용), 잔액 부족 금지
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
    }
}

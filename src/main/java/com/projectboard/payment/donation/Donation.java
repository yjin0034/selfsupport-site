package com.projectboard.payment.donation;

import com.projectboard.payment.transaction.Transaction;
import com.projectboard.payment.wallet.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 후원 엔티티
 * - 사용자 후원 정보를 저장.
 * - 후원 아이템, 유형, 상태 및 관련 메타데이터 포함.
 * - Wallet 및 Transaction과의 연관 관계 설정.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Getter
@Setter
@Builder
@Table(name = "donation")
public class Donation {

    @Id                                     // 기본 키, 자동 생성
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                        // 후원 ID (기본 키)

    @Column(nullable = false)
    private Long userId;                    // 사용자(후원자) ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DonationItem donationItem;      // 후원 물품 종류 (BOOK, FOOD, SUPPLY)

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;              // 후원 물품 가격 (금액으로 환산한 값: 교재=10000, 식료품=30000, 생활용품=50000)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DonationType donationType;      // 후원 유형 (POINT, DIRECT)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DonationStatus donationStatus;  // 후원 상태 (REQUESTED, COMPLETED, FAILED)

    @Column(nullable = false)
    private LocalDateTime createdAt;        // 생성 시각

    @Column(nullable = false)
    private LocalDateTime updatedAt;        // 수정 시각

    private String errorMessage;            // 후원 실패 시 에러 메시지

    // ===== Wallet 연관 관계 =====
    @ManyToOne(fetch = FetchType.LAZY, optional = true) // 다대일 단방향 연관 관계 (포인트 후원 시 사용, 직접 후원 시 null). optional=true로 설정하여 null 허용 (연결 관계 없어도 됨)
    @JoinColumn(name = "wallet_id")                     // 외래 키 설정
    private Wallet wallet;                              // 후원자 지갑 정보 (포인트 후원 시 사용)

    // ===== Transaction 연관 관계 =====
    @OneToOne(fetch = FetchType.LAZY, optional = true, cascade = CascadeType.ALL) // 일대일 단방향 연관 관계 (직접 후원 시 사용, 포인트 후원 시 null). 모든 영속성 전이 설정
    @JoinColumn(name = "transaction_id")                                          // 외래 키 설정
    private Transaction transaction;                                              // 연관된 트랜잭션 (포인트 후원 시 null, 직접 후원 시 결제 트랜잭션)

    // ===== Enum 정의 =====

    /**
     * 후원 아이템
     * - BOOK: 교재 (10,000원)
     * - FOOD: 식료품 (30,000원)
     * - SUPPLY: 생활용품 (50,000원)
     */
    public enum DonationItem {
        // 각 아이템과 가격 설정
        BOOK(BigDecimal.valueOf(10000)),    // 교재
        FOOD(BigDecimal.valueOf(30000)),    // 식료품
        SUPPLY(BigDecimal.valueOf(50000));  // 생활용품

        // 후원 아이템 가격
        private final BigDecimal price;

        // 생성자
        // 각 아이템에 대한 가격을 설정
        DonationItem(BigDecimal price) {
            this.price = price;
        }

        // 가격 반환 메서드
        public BigDecimal getPrice() {
            return price;
        }
    }

    /**
     * 후원 유형
     * - POINT: 포인트 후원 (사용자 지갑에서 포인트 차감)
     * - DIRECT: 직접 후원 (외부 결제 시스템을 통한 결제)
     */
    public enum DonationType {
        POINT, DIRECT;
    }

    /**
     * 후원 상태
     * - REQUESTED: 후원 요청됨
     * - COMPLETED: 후원 완료
     * - FAILED: 후원 실패
     */
    public enum DonationStatus {
        REQUESTED, COMPLETED, FAILED;
    }

}

package com.projectboard.payment.donation;

import com.projectboard.payment.order.Order;
import com.projectboard.payment.transaction.Transaction;
import com.projectboard.payment.wallet.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 후원 엔티티
 * - 사용자의 후원 내역을 저장.
 * - 후원 아이템, 금액, 유형, 상태 및 관련 메타데이터 포함.
 * - Wallet, Order, Transaction과의 연관 관계 설정.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(
        name = "donation",                                                                          // 테이블명 지정
        uniqueConstraints = @UniqueConstraint(name = "uk_donation_order", columnNames = "order_id") // order_id 컬럼에 고유 제약 조건 설정
)
public class Donation {
    // ===== 기본 필드 =====
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

    // ===== 연관 관계 설정 =====
    /**
     * 후원과 지갑의 연관 관계
     * - 포인트 후원 시 사용되는 지갑과의 다대일 단방향 연관 관계 설정.
     * - 직접 후원 시에는 null이 될 수 있도록 optional=true로 설정.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "wallet_id")                                             // 외래 키 설정
    private Wallet wallet;                                                      // 후원자 지갑 정보 (포인트 후원 시 사용)

    /**
     * 후원과 주문의 연관 관계
     * - 직접 결제 완료 시 사용되는 주문과의 일대일 단방향 연관 관계 설정.
     * - Donation가 주인, Order가 종속.
     * - 직접 후원 시에만 주문이 생성되므로
     * - 포인트 후원 시에는 null이 될 수 있도록 optional=true로 설정.
     * - 모든 영속성 전이 설정(CascadeType.ALL)하여 후원 엔티티가 저장/삭제될 때 연관된 주문 엔티티도 함께 처리.
     * - order_id 컬럼에 고유 제약 조건(Unique Constraint) 설정하여 1:1 관계 보장.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = true, cascade = CascadeType.ALL)
    @JoinColumn(
            name= "order_id",                                                   // 외래 키 설정
            foreignKey = @ForeignKey(name = "fk_donation_order")                // 외래 키 제약 조건 이름 설정
    )
    private Order order;                                                        // 연관된 주문 (포인트 후원 시 null, 직접 후원 시 주문 정보)

    /**
     * 후원과 트랜잭션의 연관 관계
     * - 직접 결제 완료 시 사용되는 트랜잭션과의 일대일 단방향 연관 관계 설정.
     * - 포인트 후원 시에는 null이 될 수 있도록 optional=true로 설정.
     * - 모든 영속성 전이 설정(CascadeType.ALL)하여 후원 엔티티가 저장/삭제될 때 연관된 트랜잭션 엔티티도 함께 처리.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = true, cascade = CascadeType.ALL)
    @JoinColumn(name = "transaction_id")                                        // 외래 키 설정
    private Transaction transaction;                                            // 연관된 트랜잭션 (포인트 후원 시 null, 직접 후원 시 결제 트랜잭션)

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

    // createdAt, updatedAt 자동 설정
    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // updatedAt 자동 갱신
    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    // ===== 도메인 규칙 =====
    // 후원 완료 처리 메서드
    public void markCompleted() {
        this.donationStatus = DonationStatus.COMPLETED; // 후원 완료로 상태 변경
    }

    // 후원 실패 처리 메서드
    public void markFailed(String message) {
        this.donationStatus = DonationStatus.FAILED;    // 후원 실패로 상태 변경
        this.errorMessage = message;                    // 실패 사유 메시지 설정
    }

    // ===== 연관 관계를 위한 Setter =====
    // 연관된 주문 설정 메서드
    public void setOrder(Order order) { this.order = order; }

    // 연관된 트랜잭션 설정 메서드
    public void setTransaction(Transaction tx) { this.transaction = tx; }

}

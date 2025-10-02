package com.projectboard.payment.order;

import com.projectboard.payment.donation.Donation;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 엔티티
 * - 사용자 주문 정보를 저장.
 * - 주문 금액, 상태 및 관련 메타데이터 포함.
 * - Donation과의 1:1 양방향 연관 관계 설정.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "orders")
public class Order {
    // ===== 기본 필드 =====
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)     // 기본 키, 자동 생성
    private Long id;                                        // 주문 ID (기본 키)

    @Column(name = "user_id", nullable = false)             // null 금지, 고유 제약 조건은 @Table에서 설정
    private Long userId;                                    // 사용자 ID

    @Column(nullable = false, precision = 19, scale = 2)    // 19자리 숫자, 소수점 이하 2자리
    private BigDecimal amount;                              // 주문 금액

    // 멱등성 보장을 위한 requestId 필드 추가
    @Column(nullable = false, unique = true)
    private String requestId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ===== 연관 관계 설정 =====
    /**
     * 주문과 후원의 연관 관계
     * - 1:1 양방향 연관 관계 설정.
     * - Donation가 주인, Order가 종속.
     * - fetch=LAZY로 설정하여 필요 시에만 로딩.
     */
    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY)
    private Donation donation;                              // 연관된 후원 엔티티

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

    // ===== 상태 변경 메서드 =====
    // 대기 상태로 변경
    public void waitStatus()    { this.status = OrderStatus.WAIT; }
    // 요청 상태로 변경
    public void requested()     { this.status = OrderStatus.REQUESTED; }
    // 승인 상태로 변경
    public void approved()      { this.status = OrderStatus.APPROVED; }
    // 실패 상태로 변경
    public void fail()          { this.status = OrderStatus.FAIL; }

}

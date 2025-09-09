package com.projectboard.payment.order;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 엔티티
 * - 사용자 주문 정보를 저장.
 * - 주문 상태 및 관련 메타데이터 포함.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "temp_order")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 기본 키, 자동 생성
    private Long id;

    @Column(name = "user_id", nullable = false) // null 금지, 고유 제약 조건은 @Table에서 설정
    private Long userId;

    private BigDecimal amount;

    private String requestId;
    private Long donationId;
    private String donationName;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

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

}

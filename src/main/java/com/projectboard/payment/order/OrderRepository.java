package com.projectboard.payment.order;

import com.projectboard.payment.wallet.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * OrderRepository
 * - 주문 관련 데이터베이스 작업을 처리하는 리포지토리 인터페이스.
 * - JpaRepository를 상속하여 기본 CRUD 기능 제공.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    // 특정 사용자의 주문 조회
    Order findByUserId(Long userId);

    // 특정 요청 ID로 주문 조회
    Order findByRequestId(String requestId);
}

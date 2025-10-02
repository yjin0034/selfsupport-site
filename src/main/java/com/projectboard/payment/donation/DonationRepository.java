package com.projectboard.payment.donation;

import com.projectboard.payment.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * DonationRepository
 * - 후원 내역을 관리하는 리포지토리 인터페이스.
 * - JpaRepository를 상속하여 기본 CRUD 기능 제공.
 * - 사용자 ID로 후원 내역 조회 및 주문으로 후원 내역 조회 기능 포함.
 */
@Repository
public interface DonationRepository extends JpaRepository<Donation, Long> {
    // 사용자 ID로 후원 내역 조회 (최신순)
    List<Donation> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 주문으로 후원 내역 조회
    Optional<Donation> findByOrder(Order order);
}

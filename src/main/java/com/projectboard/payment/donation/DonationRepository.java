package com.projectboard.payment.donation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * DonationRepository
 * - 후원 관련 데이터베이스 작업을 처리하는 리포지토리 인터페이스.
 * - JpaRepository를 상속하여 기본 CRUD 기능 제공.
 */
@Repository
public interface DonationRepository extends JpaRepository<Donation, Long> {

    // 사용자 ID로 후원 내역 조회 (최신순)
    List<Donation> findByUserIdOrderByCreatedAtDesc(Long userId);
}

package com.projectboard.payment.retry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 재시도 요청 리포지토리
 * - 재시도 요청 엔티티에 대한 CRUD 작업을 수행하는 JPA 리포지토리 인터페이스.
 */
@Repository
public interface RetryRequestRepository extends JpaRepository<RetryRequest, Long> {
    // TODO: status 와 type 으로 조회하는 메서드 추가 필요
    // 실패한 데이터 주기적으로 조회 -> IN_PROGRESS

}

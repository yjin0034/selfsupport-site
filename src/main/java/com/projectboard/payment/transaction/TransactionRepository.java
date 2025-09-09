package com.projectboard.payment.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * TransactionRepository
 * - 트랜잭션 관련 데이터베이스 작업을 처리하는 리포지토리 인터페이스.
 * - JpaRepository를 상속하여 기본 CRUD 기능 제공.
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findTransactionByOrderId(String orderId);
}

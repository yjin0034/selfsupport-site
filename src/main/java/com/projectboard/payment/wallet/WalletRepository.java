package com.projectboard.payment.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    // 특정 사용자의 지갑 조회
    Optional<Wallet> findWalletByUserId(Long userId);

    // 특정 사용자의 지갑 개수 조회
    long countByUserId(Long userId); // 테스트/검증에 유용(동시성 테스트)

    // 특정 사용자의 모든 지갑 조회
    List<Wallet> findAllByUserId(Long userId); // 실제 서비스에는 불필요, 테스트용
}

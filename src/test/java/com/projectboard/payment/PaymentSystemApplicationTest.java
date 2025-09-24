package com.projectboard.payment;

import com.projectboard.payment.wallet.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * PaymentSystemApplicationTest
 * - 스프링 컨텍스트 로딩 및 기본 리포지토리 동작 확인용 테스트 클래스
 * - application-payment-test.properties 설정 사용
 */
@SpringBootTest
@ActiveProfiles("payment-test")     // application-payment-test.properties 설정 사용
class PaymentSystemApplicationTest {
    // ===== 의존성 주입 =====
    @Autowired WalletRepository walletRepository;   // 지갑 리포지토리

    // 스프링 컨텍스트 로딩 및 기본 리포지토리 동작 확인
    @Test
    void contextLoads() {
        System.out.println(walletRepository.findAll());
    }

}

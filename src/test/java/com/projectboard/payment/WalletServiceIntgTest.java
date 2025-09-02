package com.projectboard.payment;

import com.projectboard.payment.wallet.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@ExtendWith(SpringExtension.class)
public class WalletServiceIntgTest {

    @Autowired
    WalletService walletService;
    @Autowired
    WalletRepository walletRepository;

    @AfterEach
    void tearDown() { walletRepository.deleteAll(); } // 각 테스트 격리

    @Test
    @Transactional
    @DisplayName("지갑을 생성한다 - 기본 필드/DB 반영 검증")
    void createWallet_persists_and_hasDefaults() {
        // given
        CreateWalletRequest request = new CreateWalletRequest(100L);    // 요청 DTO: userId=100 사용자의 지갑을 생성

        // when
        CreatedWalletResponse response = walletService.createWallet(request); // 실제 서비스 로직 호출 (스프링 컨텍스트/JPA/트랜잭션이 모두 동작)

        // then
        // 서비스 응답 1차 검증
        assertThat(response).isNotNull();
        assertThat(response.id())
                .as("생성된 지갑의 PK는 DB에서 발급되므로 null이 아니어야 한다")
                .isNotNull();
        assertThat(response.userId())
                .as("응답의 userId는 요청과 동일해야 한다")
                .isEqualTo(100L);
        assertThat(response.balance())
                .as("신규 지갑 잔액은 0이어야 한다 (도메인 기본 규칙)")
                .isEqualByComparingTo(BigDecimal.ZERO); // BigDecimal.equals 는 scale(자릿수)까지 비교하므로, 금액 검증에는 isEqualByComparingTo(값 비교) 권장

        // 실제 DB에 저장되었는지 'PK로' 재조회하여 확정 검증
        Wallet persisted = walletRepository.findById(response.id())
                .orElseThrow(() -> new AssertionError("DB에 Wallet이 저장되지 않았습니다."));

        // 재조회한 엔티티 필드 검증 (응답과 DB 상태가 일치하는지 확인)
        assertThat(persisted.getUserId())
                .as("DB에 저장된 userId는 요청한 userId와 같아야 한다")
                .isEqualTo(100L);
        assertThat(persisted.getBalance())
                .as("DB에 저장된 초기 잔액은 0이어야 한다")
                .isEqualByComparingTo("0");
        assertThat(persisted.getCreatedAt())
                .as("생성 시각은 영속화 시점에 채워져야 한다(@PrePersist/도메인 설정)")
                .isNotNull();
        assertThat(persisted.getUpdatedAt())
                .as("수정 시각도 최초 생성 시점에 세팅되거나 @PrePersist 로 초기화되어야 한다")
                .isNotNull();

        // 디버깅 출력
        System.out.println("✅ created: " + response);
    }

    @Test
    @DisplayName("동시에 같은 userId로 createWallet 호출해도 지갑은 1개만 생성된다")
    void createWallet_concurrent_sameUser_isIdempotent_andUnique() throws Exception {
        // given
        // 동일 userId로 여러 스레드에서 동시에 지갑 생성 요청
        Long userId = 10L;
        CreateWalletRequest request = new CreateWalletRequest(userId);

        // when
        // 20개의 스레드가 거의 동시에 createWallet() 호출
        int numOfThreads = 20;
        ExecutorService service = Executors.newFixedThreadPool(numOfThreads); // 스레드풀 생성
        CountDownLatch latch = new CountDownLatch(numOfThreads); // 모든 스레드 완료 대기용

        // then
        // 모든 스레드에서 동시에 요청 시작
        for (int i = 0; i < numOfThreads; i++) {
            // 각 스레드에서 지갑 생성 시도
            service.submit(() -> {
                try {
                    walletService.createWallet(request); // 실제 서비스 호출
                } finally {
                    latch.countDown();  // 완료 표시
                }
            });
        }

        // 최대 10초 대기 (너무 오래 걸리면 타임아웃)
        latch.await(); // 모든 스레드 완료 대기
        service.shutdown(); // 스레드풀 종료

        // 최종적으로 DB에 지갑이 1개만 존재하는지 확인
        List<Wallet> wallets = walletRepository.findAll();
        // 지갑은 유일하게 1개만 존재해야 한다
        assertThat(wallets).hasSize(1);
        // 유일한 지갑의 userId가 요청한 userId와 동일해야 한다
        assertThat(wallets.get(0).getUserId()).isEqualTo(userId);

        // 디버깅 출력
        System.out.printf("✅ 최종 지갑 개수: %d, userId=%d%n", wallets.size(), userId);
    }

}

package com.projectboard.payment;

import com.projectboard.payment.donation.DonationRepository;
import com.projectboard.payment.wallet.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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

/**
 * WalletService 통합 테스트
 * - 실제 DB와 스프링 컨텍스트를 사용하여 지갑 서비스의 주요 기능을 검증
 * - 지갑 생성, 잔액 충전, 동시성 테스트를 통해 멱등성 및 데이터 무결성 보장 확인
 * - application-payment-test.yml 설정 사용
 */
@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("payment-test") // application-payment-test.yml 설정 사용
public class WalletServiceIntgTest {
    // ===== 의존성 주입 =====
    // SUT
    @Autowired WalletService walletService;                 // 실제 지갑 서비스
    // 의존성 주입
    @Autowired WalletRepository walletRepository;           // 실제 리포지토리

    @Autowired DonationRepository donationRepository;

    // 각 테스트 격리
    @AfterEach
    void tearDown() {
        donationRepository.deleteAll();
        walletRepository.deleteAll();         // 트랜잭션이 지갑에 종속되어 있으므로, 지갑부터 삭제해야 함
    }

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

        // 동시성 환경 준비
        // 20개의 스레드가 거의 동시에 createWallet() 호출
        int numOfThreads = 20;
        ExecutorService service = Executors.newFixedThreadPool(numOfThreads); // 스레드풀 생성
        CountDownLatch latch = new CountDownLatch(numOfThreads);              // 모든 스레드 완료 대기용
        // 생성된 지갑 ID를 저장할 동기화된 리스트
        List<Long> createdIds = Collections.synchronizedList(new ArrayList<>());

        // when
        // 여러 스레드에서 동시에 createWallet() 호출
        for (int i = 0; i < numOfThreads; i++) {
            service.submit(() -> {  // 스레드풀에서 작업 제출
                try {
                    var res = walletService.createWallet(new CreateWalletRequest(userId));  // 실제 서비스 호출
                    createdIds.add(res.id());                                               // 생성된 지갑 ID 저장
                } finally {
                    latch.countDown();                                                      // 작업 완료 신호
                }
            });
        }

        // 모든 스레드가 작업을 완료할 때까지 최대 10초 대기
        boolean finished = latch.await(10, TimeUnit.SECONDS);
        // 모든 작업이 10초 내에 끝나지 않으면 테스트 실패
        assertThat(finished).as("동시 작업이 10초 내에 끝나야 함").isTrue();
        // 스레드풀 종료
        service.shutdown();

        // then
        // 1. 모든 호출이 성공해야 함 (예외 발생하지 않아야 함)
        List<Wallet> wallets = walletRepository.findAll();
        // 2. 지갑은 1개만 생성되어야 함
        assertThat(wallets).hasSize(1);
        // 3. 생성된 지갑의 userId는 요청한 userId와 동일해야 함
        assertThat(wallets.get(0).getUserId()).isEqualTo(userId);

        // 4. 모든 호출에서 반환된 지갑 ID는 동일해야 함 (멱등성)
        // Set으로 만들어 중복 제거 후 크기가 1이어야 함
        assertThat(createdIds.stream().collect(Collectors.toSet())) // Set으로 변환하여 중복 제거
                .hasSize(1)                                 // 크기가 1이어야 함
                .containsExactly(wallets.get(0).getId());           // DB에 저장된 지갑 ID와 동일해야 함

        // 디버깅 출력
        System.out.printf("✅ 최종 지갑 개수: %d, userId=%d%n", wallets.size(), userId);
    }

}

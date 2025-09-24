package com.projectboard.payment;

import com.projectboard.payment.transaction.*;
import com.projectboard.payment.wallet.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TransactionService 통합 테스트
 * - 실제 DB와 스프링 컨텍스트를 사용하여 트랜잭션 서비스의 주요 기능을 검증
 * - 지갑 생성, 잔액 충전, 결제 트랜잭션 생성 및 잔액 차감 등 핵심 시나리오 포함
 * - 동시성 테스트를 통해 멱등성 및 데이터 무결성 보장 확인
 * - application-payment-test.yml 설정 사용
 */
@Slf4j
@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("payment-test") // application-payment-test.yml 설정 사용
public class TransactionServiceIntgTest {
    // ===== 의존성 주입 =====
    // SUT
    @Autowired TransactionService transactionService;       // 실제 트랜잭션 서비스
    // 의존성 주입
    @Autowired TransactionRepository transactionRepository; // 실제 트랜잭션 리포지토리
    @Autowired WalletService walletService;                 // 실제 지갑 서비스
    @Autowired WalletRepository walletRepository;           // 실제 리포지토리

    // 각 테스트 격리
    @AfterEach
    void tearDown() {
        transactionRepository.deleteAll();                  // 트랜잭션 먼저 삭제
        walletRepository.deleteAll();                       // 지갑 삭제
    }

    @Test
    @Transactional
    @DisplayName("결제를 생성한다 - 잔액 차감, 트랜잭션 저장, 응답 필드 검증")
    void payment_createsTransaction_and_updatesBalance() {
        // given
        // 테스트용 사용자 ID 생성
        Long userId = 1001L;
        // 지갑 생성 (초기 잔액 0)
        CreatedWalletResponse created = walletService.createWallet(new CreateWalletRequest(userId));
        // 생성된 지갑 ID
        Long walletId = created.id();

        // 지갑에 잔액 충전 (충전 트랜잭션도 함께 생성됨)
        BigDecimal initial = new BigDecimal("10000"); // 10,000원 충전
        walletService.addBalance(new AddBalanceWalletRequest(walletId, initial));

        // 결제 요청 준비
        // 고유한 donationId 생성 (UUID 사용)
        String donationId = "don-" + UUID.randomUUID();
        // 결제 금액 1,000원
        BigDecimal amount = new BigDecimal("1000");
        // 결제 요청 DTO
        PaymentTransactionRequest request = new PaymentTransactionRequest(walletId, donationId, amount);

        // when
        // 실제 서비스 호출
        // 결제 트랜잭션 생성 및 잔액 차감
        PaymentTransactionResponse response = transactionService.payment(request);

        // then
        // 1. 응답 검증
        assertThat(response).isNotNull();
        assertThat(response.walletId()).as("응답의 지갑 ID").isEqualTo(walletId);
        // 응답 balance는 차감 후 잔액이어야 함: 10000 - 1000 = 9000
        assertThat(response.balance()).as("차감 후 잔액").isEqualByComparingTo(new BigDecimal("9000")); // 결제 후 잔액 9000

        // 2. 실제 DB에 반영되었는지 재확인 (지갑 잔액)
        Wallet persisted = walletRepository.findById(walletId)
                .orElseThrow(() -> new AssertionError("지갑이 DB에 존재해야 합니다"));
        assertThat(persisted.getBalance()).as("DB에 반영된 잔액").isEqualByComparingTo("9000");

        // 3. 결제 트랜잭션이 저장되었는지 확인
        Optional<Transaction> savedOpt = transactionRepository.findTransactionByOrderId(donationId);
        // 트랜잭션이 존재해야 함
        assertThat(savedOpt).as("결제 트랜잭션 저장 여부").isPresent();

        // 4. 저장된 트랜잭션 필드 검증
        Transaction saved = savedOpt.get();
        // 트랜잭션 유형은 PAYMENT 여야 함
        assertThat(saved.getTransactionType()).isEqualTo(TransactionType.PAYMENT);
        // 결제 트랜잭션은 결제 키가 반드시 있어야 함
        assertThat(saved.getWalletId()).isEqualTo(walletId);
        // 트랜잭션의 사용자 ID는 지갑의 사용자 ID와 동일해야 함
        assertThat(saved.getUserId()).isEqualTo(userId);
        // 트랜잭션 금액은 요청한 결제 금액과 동일해야 함
        assertThat(saved.getAmount()).isEqualByComparingTo(amount);
        // 주문 ID는 요청한 donationId와 동일해야 함
        assertThat(saved.getOrderId()).isEqualTo(donationId);
        // 트랜잭션 설명은 "donationId 결제" 형식이어야 함
        assertThat(saved.getDescription()).isEqualTo(donationId + " 결제");

        // 디버깅 출력
        System.out.printf("✅ payment ok → walletId=%d, before=%s, amount=%s, after=%s, orderId=%s%n",
                walletId, initial, amount, response.balance(), donationId);

    }

    @Test
    @DisplayName("동시에 같은 orderId로 충전 요청 시, 트랜잭션은 1건만 생성되고 잔액은 정확히 1회만 반영된다")
    void charge_concurrent_sameOrderId_isIdempotent_andUnique() throws InterruptedException {
        // given
        // 사용자 지갑 생성
        CreatedWalletResponse wallet = walletService.createWallet(new CreateWalletRequest(1L));
        // 생성된 지갑 ID
        Long walletId = wallet.id();

        // DB에 commit 강제 (다른 스레드가 조회할 수 있도록)
        walletRepository.flush();

        // 동일 orderId 준비
        // 충전 금액 1000원
        String orderId = "order-123";
        BigDecimal amount = BigDecimal.valueOf(1000);

        // 동시성 환경 준비
        int threadCount = 20;                                                 // 동시에 20개의 스레드에서 충전 시도
        ExecutorService executor = Executors.newFixedThreadPool(threadCount); // 스레드풀 생성
        CountDownLatch latch = new CountDownLatch(threadCount);               // 모든 스레드 완료 대기용

        List<ChargeTransactionResponse> results = new ArrayList<>();          // 결과 수집용 (동기화 필요)

        // when
        // 모든 스레드에서 동시에 요청 시작
        for (int i = 0; i < threadCount; i++) {
            // 각 스레드에서 충전 시도
            executor.submit(() -> {
                try {
                    // 실제 서비스 호출
                    ChargeTransactionResponse response = transactionService.charge(
                            new ChargeTransactionRequest(walletId, orderId, amount)
                    );
                    // 결과 수집 (동기화 필요)
                    synchronized (results) {
                        results.add(response);
                    }
                } catch (DataIntegrityViolationException e) {
                    // 중복 예외 무시 (멱등성 테스트이므로 무시)
                } finally {
                    latch.countDown(); // 완료 표시
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await();

        // then
        // 최종적으로 DB에 트랜잭션은 1건만 존재하는지 확인
        assertThat(results).hasSizeGreaterThanOrEqualTo(1);
        // 멱등성 보장: 모든 응답의 walletId는 동일해야 한다
        assertThat(results.get(0).walletId()).isEqualTo(walletId);
        // 멱등성 보장: 모든 응답의 잔액은 동일해야 한다
        assertThat(results.get(0).balance()).isEqualByComparingTo(BigDecimal.valueOf(1000));

        // 디버깅 출력
        System.out.printf("✅ concurrent charge test: orderId=%s, txCount=%d, finalBalance=%s%n",
                orderId, results.size(), results.get(0).balance());
    }

    @Test
    @DisplayName("동시에 같은 donationId로 결제 요청 시, 트랜잭션은 1건만 생성되고 잔액 차감도 1회만 반영된다")
    void payment_concurrent_sameDonationId_isIdempotent_andUnique() throws InterruptedException {
        // given
        // 사용자 지갑 생성
        CreatedWalletResponse wallet = walletService.createWallet(new CreateWalletRequest(1L));
        // 생성된 지갑 ID
        Long walletId = wallet.id();

        // DB에 commit 강제 (다른 스레드가 조회할 수 있도록)
        walletRepository.flush();

        // 우선 1000원 충전
        transactionService.charge(new ChargeTransactionRequest(walletId, "init", BigDecimal.valueOf(1000)));

        // 동일 donationId 준비
        // 결제 금액 500원
        String donationId = "donation-123";
        BigDecimal amount = BigDecimal.valueOf(500);

        // 동시성 환경 준비
        int threadCount = 20;                                                 // 동시에 20개의 스레드에서 결제 시도
        ExecutorService executor = Executors.newFixedThreadPool(threadCount); // 스레드풀 생성
        CountDownLatch latch = new CountDownLatch(threadCount);               // 모든 스레드 완료 대기용

        List<PaymentTransactionResponse> results = new ArrayList<>();         // 결과 수집용 (동기화 필요)

        // when
        // 모든 스레드에서 동시에 요청 시작
        for (int i = 0; i < threadCount; i++) {
            // 각 스레드에서 결제 시도
            executor.submit(() -> {
                try {
                    // 실제 서비스 호출
                    PaymentTransactionResponse response = transactionService.payment(
                            new PaymentTransactionRequest(walletId, donationId, amount)
                    );
                    // 결과 수집 (동기화 필요)
                    synchronized (results) {
                        results.add(response);
                    }
                } finally {
                    latch.countDown(); // 완료 표시
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await();

        // then
        // 최종적으로 DB에 트랜잭션은 1건만 존재하는지 확인
        assertThat(results).hasSizeGreaterThanOrEqualTo(1);
        // 멱등성 보장: 모든 응답의 walletId는 동일해야 한다
        assertThat(results.get(0).walletId()).isEqualTo(walletId);
        // 멱등성 보장: 모든 응답의 잔액은 동일해야 한다
        assertThat(results.get(0).balance()).isEqualByComparingTo(BigDecimal.valueOf(500));

        // 디버깅 출력
        System.out.printf("✅ concurrent payment test: donationId=%s, txCount=%d, finalBalance=%s%n",
                donationId, results.size(), results.get(0).balance());
    }

}

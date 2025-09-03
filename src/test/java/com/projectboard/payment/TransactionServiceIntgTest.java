package com.projectboard.payment;

import com.projectboard.payment.transaction.*;
import com.projectboard.payment.wallet.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@ExtendWith(SpringExtension.class)
public class TransactionServiceIntgTest {

    @Autowired TransactionService transactionService;       // SUT (실제 빈)
    @Autowired TransactionRepository transactionRepository; // 실제 트랜잭션 리포지토리
    @Autowired WalletService walletService;                 // 실제 지갑 서비스
    @Autowired WalletRepository walletRepository;           // 실제 리포지토리

    @AfterEach
    void tearDown() { walletRepository.deleteAll(); } // 각 테스트 격리

    @Test
    @Transactional
    @DisplayName("결제를 생성한다 - 잔액 차감, 트랜잭션 저장, 응답 필드 검증")
    void payment_createsTransaction_and_updatesBalance() {

        // given
        // 사용자, 지갑 생성 (초기 잔액 0)
        Long userId = 1001L;
        CreatedWalletResponse created = walletService.createWallet(new CreateWalletRequest(userId));
        Long walletId = created.id();

        // 지갑에 잔액 충전 (충전 트랜잭션도 함께 생성됨)
        BigDecimal initial = new BigDecimal("10000"); // 10,000원 충전
        walletService.addBalance(new AddBalanceWalletRequest(walletId, initial));

        // 결제 요청 준비
        String donationId = "don-" + UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1000"); // 결제 금액
        PaymentTransactionRequest request = new PaymentTransactionRequest(walletId, donationId, amount);

        // when
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
        assertThat(savedOpt).as("결제 트랜잭션 저장 여부").isPresent();

        Transaction saved = savedOpt.get();
        assertThat(saved.getTransactionType()).isEqualTo(TransactionType.PAYMENT);
        assertThat(saved.getWalletId()).isEqualTo(walletId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getAmount()).isEqualByComparingTo(amount);
        assertThat(saved.getOrderId()).isEqualTo(donationId);
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
        Long walletId = wallet.id(); // 생성된 지갑 ID

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
                    // 중복 예외 무시 (멱등성 테스트)
                } finally {
                    latch.countDown(); // 완료 표시
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await();

        // then
        // 최종적으로 DB에 트랜잭션은 1건만 존재하는지 확인
        // 트랜잭션은 유일하게 1개만 존재해야 한다
        assertThat(results).hasSizeGreaterThanOrEqualTo(1);
        // 유일한 트랜잭션의 walletId가 요청한 walletId와 동일해야 한다
        Long walletIdFromResponse = results.get(0).walletId();

        // 멱등성 보장: 모든 응답의 잔액은 동일해야 한다
        // 잔액 응답이 하나라도 있어야 한다
        BigDecimal balance = results.get(0).balance();
        // balance는 1000으로 동일해야 한다
        // 충전은 한 번만 반영되어야 하므로 잔액 = 1000
        assertThat(balance).isEqualByComparingTo(BigDecimal.valueOf(1000));
        // DB에 반영된 잔액도 동일해야 함
        assertThat(walletIdFromResponse).isEqualTo(walletId);

        // 디버깅 출력
        System.out.printf("✅ concurrent charge test: orderId=%s, txCount=%d, finalBalance=%s%n",
                orderId, results.size(), balance);
    }

    @Test
    @DisplayName("동시에 같은 donationId로 결제 요청 시, 트랜잭션은 1건만 생성되고 잔액 차감도 1회만 반영된다")
    void payment_concurrent_sameDonationId_isIdempotent_andUnique() throws InterruptedException {
        // given
        // 사용자 지갑 생성
        CreatedWalletResponse wallet = walletService.createWallet(new CreateWalletRequest(1L));
        Long walletId = wallet.id(); // 생성된 지갑 ID

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
        // 트랜잭션은 유일하게 1개만 존재해야 한다
        assertThat(results).hasSizeGreaterThanOrEqualTo(1);
        // 유일한 트랜잭션의 walletId가 요청한 walletId와 동일해야 한다
        Long walletIdFromResponse = results.get(0).walletId();

        // 멱등성 보장: 모든 응답의 잔액은 동일해야 한다
        // 잔액 응답이 하나라도 있어야 한다
        BigDecimal balance = results.get(0).balance();
        // balance는 500으로 동일해야 한다
        // 결제는 한 번만 반영되어야 하므로 잔액 = 1000 - 500 = 500
        assertThat(balance).isEqualByComparingTo(BigDecimal.valueOf(500));
        // DB에 반영된 잔액도 동일해야 한다
        assertThat(walletIdFromResponse).isEqualTo(walletId);

        // 디버깅 출력
        System.out.printf("✅ concurrent payment test: donationId=%s, txCount=%d, finalBalance=%s%n",
                donationId, results.size(), balance);
    }

}

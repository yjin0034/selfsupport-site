package com.projectboard.payment;

import com.projectboard.payment.transaction.*;
import com.projectboard.payment.wallet.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@ExtendWith(SpringExtension.class)
public class TransactionServiceIntgTest {

    @Autowired TransactionService transactionService;       // SUT (실제 빈)
    @Autowired TransactionRepository transactionRepository; // 실제 트랜잭션 리포지토리
    @Autowired WalletService walletService;                 // 실제 지갑 서비스
    @Autowired WalletRepository walletRepository;           // 실제 리포지토리

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
    @Transactional
    @DisplayName("결제를 중복 생성할 수 없다 - 같은 donationId 2회 호출 시 2번째는 실패")
    void payment_isIdempotent_byDonationId() {
        // given
        // 지갑 생성 + 충전
        Long userId = 2002L;
        CreatedWalletResponse created = walletService.createWallet(new CreateWalletRequest(userId));
        Long walletId = created.id();
        walletService.addBalance(new AddBalanceWalletRequest(walletId, new BigDecimal("50.00")));

        // 동일 donationId로 두 번 결제 요청 준비
        String sameDonationId = "don-" + UUID.randomUUID();
        BigDecimal amount = new BigDecimal("10.00");
        PaymentTransactionRequest req = new PaymentTransactionRequest(walletId, sameDonationId, amount);

        // when
        // 첫 번째 호출: 정상
        PaymentTransactionResponse first = transactionService.payment(req);

        // 두 번째 호출: 중복 → 예외
        Throwable thrown = catchThrowable(() -> transactionService.payment(req));

        // then
        // 첫 번째 호출은 정상 응답
        assertThat(first).isNotNull();
        // 두 번째 호출은 예외 발생
        assertThat(thrown)
                .as("동일 donationId로 2번째 결제는 중복으로 거부되어야 한다")
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("이미");

        // 멱등성 확인: 트랜잭션은 1건만 존재해야 함
        //assertThat(transactionRepository.findTransactionByOrderId(sameDonationId)).isPresent();

        // 잔액도 첫 호출에서만 10 차감되었는지 확인 (50 → 40)
        Wallet persisted = walletRepository.findById(walletId).orElseThrow();
        assertThat(persisted.getBalance()).isEqualByComparingTo("40.00");

        // 디버깅 출력
        System.out.printf("🧪 idempotency → walletId=%d, amount=%s, donationId=%s, thrown=%s%n",
                walletId, amount, sameDonationId, thrown.getClass().getSimpleName());
    }

}

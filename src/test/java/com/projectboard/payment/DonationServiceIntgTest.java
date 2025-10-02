package com.projectboard.payment;

import com.projectboard.payment.checkout.ConfirmRequest;
import com.projectboard.payment.donation.Donation;
import com.projectboard.payment.donation.DonationRepository;
import com.projectboard.payment.donation.DonationService;
import com.projectboard.payment.transaction.TransactionRepository;
import com.projectboard.payment.wallet.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * DonationService 통합 테스트
 * - 실제 데이터베이스와 연동하여 DonationService의 주요 기능들을 통합적으로 테스트.
 * - 포인트 후원, 직접 결제 후원, 나의 후원 내역 조회 기능 포함.
 * - 외부 결제 게이트웨이 서비스는 모킹하여 실제 호출을 방지.
 * - 각 테스트는 트랜잭션 롤백 및 데이터 삭제로 격리 보장.
 */
@SpringBootTest
@ExtendWith(SpringExtension.class)
@Transactional
@ActiveProfiles("payment-test")     // application-payment-test.properties 설정 사용
public class DonationServiceIntgTest {
    // ===== 의존성 주입 =====
    // SUT
    @Autowired DonationService donationService;             // 실제 Donation 서비스
    // 의존성 주입
    @Autowired DonationRepository donationRepository;       // 실제 Donation 리포지토리
    @Autowired WalletService walletService;                 // 실제 지갑 서비스
    @Autowired WalletRepository walletRepository;           // 실제 지갑 리포지토리
    @Autowired TransactionRepository transactionRepository; // 실제 트랜잭션 리포지토리

    // 외부 결제 게이트웨이 서비스는 실제 호출을 막기 위해 모킹
    @MockBean com.projectboard.payment.external.PaymentGatewayService paymentGatewayService; // 외부 결제 게이트웨이 서비스 모킹

    // 각 테스트 격리
    // 트랜잭션 롤백 외에 추가로 데이터 삭제
    @AfterEach
    void tearDown() {
        donationRepository.deleteAll();                 // 후원 기록 삭제
        transactionRepository.deleteAll();              // 트랜잭션 기록 삭제
        walletRepository.deleteAll();                   // 지갑 기록 삭제
    }

    @Test
    @DisplayName("포인트 후원 성공 - 지갑 잔액 차감, 후원 저장, 트랜잭션 저장")
    void donateWithPoint_success() {
        // given
        // 1. 사용자 및 지갑 생성
        Long userId = 101L;                                                                            // 사용자 ID
        CreatedWalletResponse wallet = walletService.createWallet(new CreateWalletRequest(userId));    // 지갑 생성
        // 2. 지갑에 잔액 충전
        walletService.addBalance(new AddBalanceWalletRequest(wallet.id(), BigDecimal.valueOf(20000))); // 지갑에 20,000원 충전
        // 3. 후원 아이템 선택
        Donation.DonationItem item = Donation.DonationItem.BOOK;                                       // 후원 아이템 (책 - 10,000원)

        // when
        // 실제 서비스 호출
        // 포인트로 후원
        Donation donation = donationService.donateWithPoint(userId, item);

        // then
        // 1. 후원 기록 검증
        // 후원 기록이 생성되고 상태가 COMPLETED여야 함
        assertThat(donation).isNotNull();
        // 후원 ID가 생성되어야 함
        assertThat(donation.getDonationStatus()).isEqualTo(Donation.DonationStatus.COMPLETED);

        // 2. 지갑 잔액 검증
        // 지갑 잔액이 10,000원 차감되어야 함
        Wallet persisted = walletRepository.findById(wallet.id())
                .orElseThrow();
        // 초기 20,000 - 후원 10,000 = 10,000 남아야 함
        assertThat(persisted.getBalance()).isEqualByComparingTo("10000");

        // 3. DB에 후원 기록이 저장되었는지 재확인
        // 모든 후원 기록 조회
        List<Donation> all = donationRepository.findAll();
        // 후원 기록이 1건 있어야 함
        assertThat(all).hasSize(1);
        // 저장된 후원 기록이 방금 생성한 후원 기록과 동일해야 함
        assertThat(all.get(0).getDonationItem()).isEqualTo(item);

        // 디버깅 출력
        System.out.printf("✅ point donation ok → userId=%d, balanceAfter=%s, donationId=%d%n",
                userId, persisted.getBalance(), donation.getId());
    }

    @Test
    @DisplayName("포인트 후원 실패 - 잔액 부족 시 Donation(FAILED) 저장")
    void donateWithPoint_insufficientBalance_thenFail() {
        // given
        // 1. 사용자 및 지갑 생성 (초기 잔액=0)
        Long userId = 102L;                                          // 사용자 ID
        walletService.createWallet(new CreateWalletRequest(userId)); // 지갑 생성 (잔액 0)
        // 2. 후원 아이템 선택 (책=10000, 식료품=30000, 생활용품=50000)
        Donation.DonationItem item = Donation.DonationItem.FOOD;     // 후원 아이템 (식료품 - 30,000원, 잔액 부족)

        // when
        // 실제 서비스 호출
        // 잔액 부족으로 후원 실패 예상
        Donation result = donationService.donateWithPoint(userId, item);

        // then
        // 1. 반환값 검증
        // 후원 기록이 생성되어야 함
        assertThat(result).isNotNull();
        // 후원 상태가 FAILED여야 함
        assertThat(result.getDonationStatus()).isEqualTo(Donation.DonationStatus.FAILED);
        // 에러 메시지에 "잔액" 포함되어야 함
        assertThat(result.getErrorMessage()).contains("잔액");

        // 2. DB에 후원 기록이 저장되었는지 재확인
        // 모든 후원 기록 조회
        List<Donation> all = donationRepository.findAll();
        // 후원 기록이 1건 있어야 함
        assertThat(all).hasSize(1);
        // 저장된 후원 기록이 방금 생성한 후원 기록과 동일해야 함
        assertThat(all.get(0).getId()).isEqualTo(result.getId());
        // 저장된 후원 기록의 상태가 FAILED여야 함
        assertThat(all.get(0).getDonationStatus()).isEqualTo(Donation.DonationStatus.FAILED);

        // 3. 지갑 잔액이 변동 없는지 재확인
        // 지갑 재조회
        Wallet persisted = walletRepository.findWalletByUserId(userId).orElseThrow();
        // 잔액이 여전히 0이어야 함
        assertThat(persisted.getBalance()).isEqualByComparingTo("0");

        // 디버깅 출력
        System.out.printf("✅ point donation fail → userId=%d, status=%s, error=%s%n",
                userId, result.getDonationStatus(), result.getErrorMessage());
    }

    @Test
    @DisplayName("직접 결제 후원 성공 - PG 승인 후 Donation 저장")
    void donateWithPayment_success() {
        // given
        // 1. 사용자 생성 (직접 결제는 지갑 없어도 됨)
        Long userId = 103L;
        // 2. 고유한 orderId 생성
        String orderId = "order-" + UUID.randomUUID();
        // 3. 결제 승인 요청 준비
        // 실제 PG 서비스는 모킹했으므로 payKey는 아무 값이나 가능
        // amount는 후원 아이템 가격과 동일해야 함
        ConfirmRequest confirmRequest = new ConfirmRequest("payKey-123", orderId, "10000");
        // 4. 후원 아이템 선택 (책=10000, 식료품=30000, 생활용품=50000)
        Donation.DonationItem item = Donation.DonationItem.BOOK;

        // when
        // 실제 서비스 호출
        // 직접 결제로 후원
        Donation donation = donationService.donateWithPayment(userId, confirmRequest, item);

        // then
        // 1. 후원 기록 검증
        // 후원 기록이 생성되어야 함
        assertThat(donation).isNotNull();
        // 후원 ID가 생성되어야 함
        assertThat(donation.getId()).isNotNull();
        // 후원 아이템이 요청한 아이템과 동일해야 함
        assertThat(donation.getDonationItem()).isEqualTo(item);
        // 후원 상태가 REQUESTED여야 함 (결제 승인 후 바로 COMPLETED로 변경되지 않음)
        assertThat(donation.getDonationStatus()).isEqualTo(Donation.DonationStatus.REQUESTED);
        // 후원 금액이 후원 아이템 가격과 동일해야 함
        assertThat(donation.getAmount()).isEqualByComparingTo(item.getPrice());

        // 2. 트랜잭션 검증
        // 연관된 트랜잭션이 생성되어야 함
        assertThat(donation.getTransaction()).isNotNull();
        // 트랜잭션의 orderId가 요청한 orderId와 동일해야 함
        assertThat(donation.getTransaction().getOrderId()).isEqualTo(orderId);
        // 트랜잭션의 userId가 후원자 ID와 동일해야 함
        assertThat(donation.getTransaction().getUserId()).isEqualTo(userId);
        // 트랜잭션의 amount가 후원 아이템 가격과 동일해야 함
        assertThat(donation.getTransaction().getAmount()).isEqualByComparingTo(item.getPrice());
        // 트랜잭션 타입이 PAYMENT여야 함
        assertThat(donation.getTransaction().getTransactionType()).isEqualTo(com.projectboard.payment.transaction.TransactionType.PAYMENT);
        // 트랜잭션의 지갑 ID가 null이어야 함 (직접 결제는 지갑 없이 결제됨)
        assertThat(donation.getTransaction().getWalletId()).isNull();

        // 3. DB에 후원 기록이 저장되었는지 재확인
        // 모든 후원 기록 조회
        List<Donation> all = donationRepository.findAll();
        // 후원 기록이 1건 있어야 함
        assertThat(all).hasSize(1);
        // 후원 상태가 REQUESTED여야 함
        assertThat(all.get(0).getDonationStatus()).isEqualTo(Donation.DonationStatus.REQUESTED);
        // 연관된 트랜잭션이 존재해야 함
        assertThat(all.get(0).getTransaction()).isNotNull();

        // 디버깅 출력
        System.out.printf("✅ direct donation ok → userId=%d, donationId=%d, txOrderId=%s%n",
                userId, donation.getId(), donation.getTransaction().getOrderId());
    }

    @Test
    @DisplayName("직접 결제 후원 실패 - 동일 orderId 중복 시 예외 발생")
    void donateWithPayment_duplicateOrder_thenThrows() {
        // given
        // 1. 사용자 생성 (직접 결제는 지갑 없어도 됨)
        Long userId = 104L;
        // 2. 고유한 orderId 생성
        String orderId = "dup-" + UUID.randomUUID();
        // 3. 결제 승인 요청 준비
        // 실제 PG 서비스는 모킹했으므로 payKey는 아무 값이나 가능
        // amount는 후원 아이템 가격과 동일해야 함
        ConfirmRequest confirmRequest = new ConfirmRequest("payKey-dup", orderId, "10000");
        // 4. 후원 아이템 선택 (책=10000, 식료품=30000, 생활용품=50000)
        Donation.DonationItem item = Donation.DonationItem.BOOK;

        // 5. 먼저 정상 후원 처리 (동일 orderId로 중복 요청 테스트 위해)
        // 정상 후원 1회 처리
        donationService.donateWithPayment(userId, confirmRequest, item);

        // when
        // 실제 서비스 호출
        // 동일 orderId로 다시 후원 시도 (중복 요청)
        // 중복 요청으로 예외 발생 예상
        // 예외 잡기 위해 catchThrowable 사용
        Throwable thrown = catchThrowable(() -> donationService.donateWithPayment(userId, confirmRequest, item));

        // then
        // 1. 예외 검증
        // 중복 요청으로 IllegalArgumentException 발생 예상
        // 예외 메시지에 "이미 처리된" 포함되어야 함
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 처리된");

        // 2. DB에 후원 기록이 1건만 존재하는지 재확인
        // 모든 후원 기록 조회
        long count = donationRepository.count();
        // 후원 기록이 1건만 있어야 함 (중복 생성 안 됨)
        assertThat(count).isEqualTo(1); // 중복 생성 안 됨

        // 디버깅 출력
        System.out.printf("✅ duplicate donation blocked → orderId=%s, error=%s%n",
                orderId, thrown.getMessage());
    }

    @Test
    @DisplayName("나의 후원 내역 조회 - 최신순 반환")
    void getMyDonations_returnsSortedList() {
        // given
        // 1. 사용자 및 지갑 생성
        Long userId = 105L;
        walletService.createWallet(new CreateWalletRequest(userId)); // 지갑 생성

        // 2. 여러 후원 기록 생성 (포인트 후원 1건, 직접 결제 후원 1건)
        // 포인트 후원 (책=10000)
        ConfirmRequest c1 = new ConfirmRequest("k1", "o1", "10000");
        // 직접 결제 후원 (식료품=30000)
        ConfirmRequest c2 = new ConfirmRequest("k2", "o2", "30000");
        // 포인트 후원
        donationService.donateWithPayment(userId, c1, Donation.DonationItem.BOOK);
        // 직접 결제 후원
        donationService.donateWithPayment(userId, c2, Donation.DonationItem.FOOD);

        // when
        // 실제 서비스 호출
        // 나의 후원 내역 조회
        var result = donationService.getMyDonations(userId);

        // then
        // 1. 후원 내역 검증
        // 후원 내역이 2건 있어야 함
        assertThat(result).hasSize(2);
        // 최신 후원 내역이 첫 번째에 와야 함 (직접 결제 후원이 최신)
        assertThat(result.get(0).donationAmount()).isEqualByComparingTo("30000");
        // 두 번째 후원 내역이 두 번째에 와야 함 (포인트 후원이 이전)
        assertThat(result.get(1).donationAmount()).isEqualByComparingTo("10000");

        // 디버깅 출력
        System.out.printf("✅ donations list → size=%d, first=%s%n",
                result.size(), result.get(0));
    }

}

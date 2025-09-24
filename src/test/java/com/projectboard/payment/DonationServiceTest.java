package com.projectboard.payment;

import com.projectboard.payment.checkout.ConfirmRequest;
import com.projectboard.payment.donation.Donation;
import com.projectboard.payment.donation.DonationRepository;
import com.projectboard.payment.donation.DonationResponse;
import com.projectboard.payment.donation.DonationService;
import com.projectboard.payment.external.PaymentGatewayService;
import com.projectboard.payment.transaction.Transaction;
import com.projectboard.payment.transaction.TransactionRepository;
import com.projectboard.payment.transaction.TransactionService;
import com.projectboard.payment.wallet.Wallet;
import com.projectboard.payment.wallet.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

/**
 * DonationServiceTest
 * - DonationService의 주요 기능을 단위 테스트하는 클래스.
 * - Mockito를 사용하여 의존성을 모킹하고, 다양한 시나리오에 대한 후원 기능을 검증.
 */
@ExtendWith(MockitoExtension.class)
public class DonationServiceTest {
    // 테스트 결과 로거: 성공/실패/중단/비활성 상태 콘솔 출력
    @RegisterExtension
    static TestWatcher logWatcher = new TestWatcher() {
        @Override
        // 테스트가 성공한 경우
        public void testSuccessful(ExtensionContext context) {
            System.out.println("✅ PASSED: " + context.getDisplayName());
        }

        @Override
        // 테스트가 실패한 경우
        public void testFailed(ExtensionContext context, Throwable cause) {
            System.err.println("❌ FAILED: " + context.getDisplayName());
            cause.printStackTrace(); // 빨간 스택트레이스 출력
        }

        @Override
        // 테스트가 중단된 경우
        public void testAborted(ExtensionContext context, Throwable cause) {
            System.err.println("⚠️ ABORTED: " + context.getDisplayName());
        }

        @Override
        // 테스트가 비활성화된 경우
        public void testDisabled(ExtensionContext context, Optional<String> reason) {
            System.out.println("⏸ DISABLED: " + context.getDisplayName() +
                    reason.map(r -> " — " + r).orElse(""));
        }
    };

    // ===== 의존성 주입 =====
    // 모킹된 의존성들
    @Mock private DonationRepository donationRepository;            // 후원 리포지토리
    @Mock private WalletRepository walletRepository;                // 지갑 리포지토리
    @Mock private PaymentGatewayService paymentGatewayService;      // 외부 결제 게이트웨이 서비스
    @Mock private TransactionService transactionService;            // 거래 서비스
    @Mock private TransactionRepository transactionRepository;      // 거래 리포지토리
    // SUT
    @InjectMocks private DonationService donationService;           // 후원 서비스

    // 테스트 데이터
    private Long userId;                                            // 테스트 사용자 ID
    private Wallet wallet;                                          // 테스트 지갑

    // 테스트 데이터 초기화
    // 각 테스트 전에 실행되어 모킹된 의존성을 초기화하고 서비스 인스턴스를 생성
    @BeforeEach
    void setUp() {
        userId = 1L;                                    // 테스트 사용자 ID
        wallet = new Wallet();                          // 테스트 지갑 객체 생성
        wallet.setId(10L);                              // 지갑 ID 설정
        wallet.setUserId(userId);                       // 사용자 ID 설정
        wallet.setBalance(BigDecimal.valueOf(50000));   // 초기 잔액 50,000원 설정
        wallet.setCreatedAt(LocalDateTime.now());       // 생성 시각 설정
        wallet.setUpdatedAt(LocalDateTime.now());       // 수정 시각 설정
    }

    @Test
    @DisplayName("포인트 후원 - 성공 (잔액 충분)")
    void donateWithPoint_success() {
        // given

        // 후원 아이템 설정 (교재, 10,000원)
        Donation.DonationItem item = Donation.DonationItem.BOOK;

        // ===== 지갑 조회 및 잔액 차감 모킹 =====
        // 지갑 조회 모킹 (사용자 ID로 지갑 조회 시, 미리 생성한 지갑 반환)
        given(walletRepository.findWalletByUserId(userId)).willReturn(Optional.of(wallet));
        // 지갑 저장 모킹 (변경된 잔액 반영된 지갑 반환)
        // 잔액 차감 후, 저장된 지갑 객체 반환하도록 설정
        given(walletRepository.save(any(Wallet.class))).willReturn(wallet);

        // ===== 트랜잭션 생성 모킹 =====
        // 포인트 후원 트랜잭션 생성
        // 후원 아이템 가격에 해당하는 포인트 후원 트랜잭션 객체 생성
        Transaction tx = Transaction.createPointDonationTransaction(userId, wallet.getId(), item.getPrice());
        // 포인트 후원 트랜잭션 생성 시, 미리 생성한 트랜잭션 객체 반환하도록 설정
        given(transactionService.createPointDonationTransaction(userId, wallet.getId(), item.getPrice()))
                .willReturn(tx);

        // ===== 후원 저장 모킹 =====
        // 저장된 후원 객체 생성
        Donation savedDonation = Donation.builder()                                     // 후원 엔티티 빌더 패턴으로 생성
                .id(1L).userId(userId).donationItem(item)                               // 후원자 ID, 아이템 설정
                .amount(item.getPrice())                                                // 후원 금액 설정
                .donationType(Donation.DonationType.POINT)                              // 포인트 후원 유형 설정
                .donationStatus(Donation.DonationStatus.COMPLETED)                      // 후원 상태 완료 설정
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())          // 생성/수정 시각 설정
                .build();
        // 후원 저장 시, ID가 부여된 후원 객체 반환하도록 설정
        given(donationRepository.save(any(Donation.class))).willReturn(savedDonation);

        // when
        // 후원 서비스 호출하여 결과 받기
        Donation result = donationService.donateWithPoint(userId, item);

        // then
        // 결과 검증
        // 후원 결과가 null이 아닌지 확인
        assertThat(result).isNotNull();
        // 후원 상태가 COMPLETED인지 확인
        assertThat(result.getDonationStatus()).isEqualTo(Donation.DonationStatus.COMPLETED);
        // 후원 금액이 아이템 가격과 일치하는지 확인
        then(walletRepository).should(times(1)).save(wallet);
        // 잔액이 후원 금액만큼 차감되었는지 확인
        then(transactionService).should(times(1)).createPointDonationTransaction(userId, wallet.getId(), item.getPrice());
        // 후원 저장이 한 번 호출되었는지 확인
        then(donationRepository).should(times(1)).save(any(Donation.class));

        // 디버깅 출력
        System.out.printf("🔎 donation success: id=%d, status=%s%n", result.getId(), result.getDonationStatus());
    }

    @Test
    @DisplayName("포인트 후원 - 잔액 부족 시 실패 기록 저장 및 예외 발생")
    void donateWithPoint_insufficientBalance_thenThrows() {
        // given

        // 후원 아이템 설정 (생활용품, 50,000원)
        Donation.DonationItem item = Donation.DonationItem.SUPPLY;
        // 지갑 잔액을 후원 아이템 가격보다 적게 설정 (예: 1,000원)
        wallet.setBalance(BigDecimal.valueOf(1000));

        // ===== 지갑 조회 및 잔액 부족 모킹 =====
        // 지갑 조회 모킹 (사용자 ID로 지갑 조회 시, 미리 생성한 지갑 반환)
        given(walletRepository.findWalletByUserId(userId)).willReturn(Optional.of(wallet));

        // ===== 후원 실패 기록 저장 모킹 =====
        // 잔액 부족 시, 후원 실패 기록 저장을 위해 save 호출 시 실패한 후원 객체 반환하도록 설정
        Donation failedDonation = Donation.builder()                                // 후원 엔티티 빌더 패턴으로 생성
                .userId(userId).donationItem(item).amount(item.getPrice())          // 후원자 ID, 아이템, 금액 설정
                .donationType(Donation.DonationType.POINT)                          // 포인트 후원 유형 설정
                .donationStatus(Donation.DonationStatus.FAILED)                     // 후원 상태 실패 설정
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())      // 생성/수정 시각 설정
                .build();
        // 후원 저장 시, 실패한 후원 객체 반환하도록 설정
        given(donationRepository.save(any(Donation.class))).willReturn(failedDonation);

        // when
        // 후원 서비스 호출 시 예외 발생 캡처
        // 잔액 부족으로 인해 IllegalArgumentException 예외가 발생할 것으로 예상
        Throwable thrown = catchThrowable(() -> donationService.donateWithPoint(userId, item));

        // then
        // 예외 검증
        // 발생한 예외가 IllegalArgumentException인지 확인
        // 예외 메시지에 "잔액이 부족" 문구가 포함되어 있는지 확인
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("잔액이 부족");
        // 후원 저장이 한 번 호출되었는지 확인 (잔액 부족으로 인해 실패 기록 저장)
        then(donationRepository).should(times(1)).save(any(Donation.class));

        // 디버깅 출력
        System.out.printf("🔎 donation failed: userId=%d, item=%s, ex=%s%n",
                userId, item, thrown.getClass().getSimpleName());
    }

    @Test
    @DisplayName("직접 결제 후원 - 성공 (PG 승인)")
    void donateWithPayment_success() {
        // given

        // 후원 아이템 설정 (식료품, 30,000원)
        Donation.DonationItem item = Donation.DonationItem.FOOD;
        // 결제 승인 요청 객체 생성 (결제 키, 주문 ID, 금액)
        // 실제 결제 키와 주문 ID는 테스트용 더미 값 사용
        // 금액은 후원 아이템 가격과 일치
        ConfirmRequest confirmRequest = new ConfirmRequest("payKey", "orderId-123", "30000");

        // ===== 중복 결제 방지 모킹 =====
        // 주문 ID로 이미 처리된 거래가 없는지 확인 (중복 결제 방지)
        given(transactionRepository.existsByOrderId(confirmRequest.orderId())).willReturn(false);

        // ===== PG 승인 모킹 =====
        // PG 승인 요청 모킹 (실제 외부 호출 없이 doNothing으로 처리)
        // PG 승인 요청이 성공적으로 처리되었다고 가정
        doNothing().when(paymentGatewayService).confirm(confirmRequest);

        // ===== 트랜잭션 생성 모킹 =====
        // PG 결제 트랜잭션 생성
        Transaction tx = Transaction.createPgPaymentTransaction(userId, confirmRequest.orderId(), item.getPrice(), confirmRequest.paymentKey());
        // PG 결제 트랜잭션 생성 시, 미리 생성한 트랜잭션 객체 반환하도록 설정
        given(transactionService.pgPayment(userId, confirmRequest.orderId(), item.getPrice(), confirmRequest.paymentKey()))
                .willReturn(tx);

        // ===== 후원 저장 모킹 =====
        // 저장된 후원 객체 생성
        Donation savedDonation = Donation.builder()                                     // 후원 엔티티 빌더 패턴으로 생성
                .id(10L).userId(userId).donationItem(item).amount(item.getPrice())      // 후원자 ID, 아이템, 금액 설정
                .donationType(Donation.DonationType.DIRECT)                             // 직접 결제 후원 유형 설정
                .donationStatus(Donation.DonationStatus.COMPLETED)                      // 후원 상태 완료 설정
                .transaction(tx)                                                        // 연관된 트랜잭션 설정
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())          // 생성/수정 시각 설정
                .build();
        // 후원 저장 시, ID가 부여된 후원 객체 반환하도록 설정
        given(donationRepository.save(any(Donation.class))).willReturn(savedDonation);

        // when
        // 후원 서비스 호출하여 결과 받기
        Donation result = donationService.donateWithPayment(userId, confirmRequest, item);

        // then
        // 결과 검증
        // 후원 결과가 null이 아닌지 확인
        assertThat(result).isNotNull();
        // 후원 상태가 COMPLETED인지 확인
        assertThat(result.getDonationStatus()).isEqualTo(Donation.DonationStatus.COMPLETED);
        // 후원 금액이 아이템 가격과 일치하는지 확인
        assertThat(result.getTransaction()).isEqualTo(tx);
        // PG 승인 요청이 한 번 호출되었는지 확인
        then(paymentGatewayService).should(times(1)).confirm(confirmRequest);
        // 트랜잭션 생성이 한 번 호출되었는지 확인
        then(transactionService).should(times(1)).pgPayment(userId, confirmRequest.orderId(), item.getPrice(), confirmRequest.paymentKey());
        // 후원 저장이 한 번 호출되었는지 확인
        then(donationRepository).should(times(1)).save(any(Donation.class));

        // 디버깅 출력
        System.out.printf("🔎 donation success (PG): id=%d, status=%s, tx=%s%n",
                result.getId(), result.getDonationStatus(), result.getTransaction().getOrderId());
    }

    @Test
    @DisplayName("직접 결제 후원 - PG 승인 실패 시 실패 기록 저장 및 예외 발생")
    void donateWithPayment_pgFail_thenThrows() {
        // given

        // 후원 아이템 설정 (교재, 10,000원)
        Donation.DonationItem item = Donation.DonationItem.BOOK;
        // 결제 승인 요청 객체 생성 (결제 키, 주문 ID, 금액)
        // 실제 결제 키와 주문 ID는 테스트용 더미 값 사용
        // 금액은 후원 아이템 가격과 일치
        ConfirmRequest confirmRequest = new ConfirmRequest("payKey", "orderId-err", "10000");

        // ===== 중복 결제 방지 모킹 =====
        // 주문 ID로 이미 처리된 거래가 없는지 확인 (중복 결제 방지)
        given(transactionRepository.existsByOrderId(confirmRequest.orderId())).willReturn(false);

        // ===== PG 승인 실패 모킹 =====
        // PG 승인 요청 모킹 (실제 외부 호출 없이 예외 발생)
        // PG 승인 요청이 실패하여 RestClientException 예외 발생 시뮬레이션
        doThrow(new RestClientException("PG 승인 실패"))
                .when(paymentGatewayService).confirm(confirmRequest);

        // ===== 후원 실패 기록 저장 모킹 =====
        // 후원 저장 시, 실패한 후원 객체 반환하도록 설정
        given(donationRepository.save(any(Donation.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        // 후원 서비스 호출 시 예외 발생 캡처
        // PG 승인 실패로 인해 RestClientException 예외가 발생할 것으로 예상
        Throwable thrown = catchThrowable(() -> donationService.donateWithPayment(userId, confirmRequest, item));

        // then
        // 예외 검증
        // 발생한 예외가 RestClientException인지 확인
        assertThat(thrown).isInstanceOf(RestClientException.class);
        // 후원 저장이 한 번 호출되었는지 확인 (PG 승인 실패로 인해 실패 기록 저장)
        then(donationRepository).should(times(1)).save(any(Donation.class));

        // 디버깅 출력
        System.out.printf("🔎 donation failed (PG): userId=%d, orderId=%s, ex=%s%n",
                userId, confirmRequest.orderId(), thrown.getClass().getSimpleName());
    }

    @Test
    @DisplayName("직접 결제 후원 - 중복 orderId 시 예외 발생")
    void donateWithPayment_whenDuplicateOrderId_thenThrows() {
        // given

        // 후원 아이템 설정 (교재, 10,000원)
        Donation.DonationItem item = Donation.DonationItem.BOOK;
        // 결제 승인 요청 객체 생성 (결제 키, 주문 ID, 금액)
        // 실제 결제 키와 주문 ID는 테스트용 더미 값 사용
        // 금액은 후원 아이템 가격과 일치
        ConfirmRequest confirmRequest = new ConfirmRequest("payKey", "dup-orderId", "10000");

        // ===== 중복 결제 방지 모킹 =====
        // 주문 ID로 이미 처리된 거래가 있는 것으로 설정 (중복 결제 방지)
        given(transactionRepository.existsByOrderId(confirmRequest.orderId())).willReturn(true);

        // when
        // 후원 서비스 호출 시 예외 발생 캡처
        // 중복 orderId로 인해 IllegalArgumentException 예외가 발생할 것으로 예상
        Throwable thrown = catchThrowable(() -> donationService.donateWithPayment(userId, confirmRequest, item));

        // then
        // 예외 검증
        // 발생한 예외가 IllegalArgumentException인지 확인
        // 예외 메시지에 "이미 처리된" 문구가 포함되어 있는지 확인
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 처리된");
        // 중복 확인을 위해 orderId 존재 여부 조회가 한 번 호출되었는지 확인
        then(transactionRepository).should(times(1)).existsByOrderId(confirmRequest.orderId());

        // 디버깅 출력
        System.out.printf("🔎 duplicate donation blocked: orderId=%s, ex=%s%n",
                confirmRequest.orderId(), thrown.getClass().getSimpleName());
    }

    @Test
    @DisplayName("나의 후원 내역 조회 - 생성시각 내림차순 정렬")
    void getMyDonations_returnsSortedList() {
        // given

        // ===== 후원 기록 모킹 =====
        // 후원 기록 2개 생성 (서로 다른 생성 시각)
        // 첫 번째 후원 기록 (더 오래된 생성)
        Donation d1 = Donation.builder()                                         // 후원 엔티티 빌더 패턴으로 생성
                .id(1L).userId(userId).donationItem(Donation.DonationItem.BOOK)  // 후원자 ID, 아이템 설정
                .amount(BigDecimal.valueOf(10000))                               // 후원 금액 설정
                .donationType(Donation.DonationType.POINT)                       // 포인트 후원 유형 설정
                .donationStatus(Donation.DonationStatus.COMPLETED)               // 후원 상태 완료 설정
                .createdAt(LocalDateTime.now().minusDays(1))                     // 생성 시각을 1일 전으로 설정
                .updatedAt(LocalDateTime.now().minusDays(1))                     // 수정 시각을 1일 전으로 설정
                .build();
        // 두 번째 후원 기록 (더 최근 생성)
        Donation d2 = Donation.builder()                                         // 후원 엔티티 빌더 패턴으로 생성
                .id(2L).userId(userId).donationItem(Donation.DonationItem.FOOD)  // 후원자 ID, 아이템 설정
                .amount(BigDecimal.valueOf(30000))                               // 후원 금액 설정
                .donationType(Donation.DonationType.DIRECT)                      // 직접 결제 후원 유형 설정
                .donationStatus(Donation.DonationStatus.COMPLETED)               // 후원 상태 완료 설정
                .createdAt(LocalDateTime.now())                                  // 생성 시각을 현재 시각으로 설정
                .updatedAt(LocalDateTime.now())                                  // 수정 시각을 현재 시각으로 설정
                .build();
        // 사용자 ID로 후원 기록 조회 시, 생성 시각 내림차순으로 정렬된 두 후원 기록 반환하도록 설정
        // d2가 d1보다 최근 생성되었으므로 먼저 반환
        // List.of(d2, d1) 순서로 반환
        given(donationRepository.findByUserIdOrderByCreatedAtDesc(userId)).willReturn(List.of(d2, d1));

        // when
        // 후원 내역 조회 서비스 호출
        // 결과 받기
        List<DonationResponse> result = donationService.getMyDonations(userId);

        // then
        // 후원 내역이 2개인지 확인
        assertThat(result).hasSize(2);
        // 생성 시각 내림차순으로 정렬되었는지 확인 (d2가 d1보다 먼저)
        // 첫 번째가 d2인지 확인
        assertThat(result.get(0).donationId()).isEqualTo(d2.getId());
        // 두 번째가 d1인지 확인
        assertThat(result.get(1).donationId()).isEqualTo(d1.getId());

        // 디버깅 출력
        System.out.printf("🔎 donations list: %s%n", result);
    }

}

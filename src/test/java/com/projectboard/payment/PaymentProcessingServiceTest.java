package com.projectboard.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectboard.payment.checkout.ConfirmRequest;
import com.projectboard.payment.donation.DonationService;
import com.projectboard.payment.external.PaymentGatewayService;
import com.projectboard.payment.order.Order;
import com.projectboard.payment.order.OrderRepository;
import com.projectboard.payment.order.OrderStatus;
import com.projectboard.payment.processing.PaymentProcessingService;
import com.projectboard.payment.retry.RetryRequestRepository;
import com.projectboard.payment.retry.RetryRequest;
import com.projectboard.payment.retry.RetryRequestService;
import com.projectboard.payment.transaction.ChargeTransactionRequest;
import com.projectboard.payment.transaction.ChargeTransactionResponse;
import com.projectboard.payment.transaction.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.times;

/**
 * PaymentProcessingService 단위 테스트
 * - 결제 처리 서비스의 주요 기능을 단위 테스트로 검증
 * - PG 승인, 결제 기록 생성, 주문 상태 변경 등 핵심 시나리오 포함
 * - Timeout 예외 발생 시 재시도 요청 저장 및 재시도 후 정상 처리 검증
 * - Mockito를 사용하여 외부 의존성 모킹
 */
@ExtendWith(MockitoExtension.class)
public class PaymentProcessingServiceTest {
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
    // SUT
    private PaymentProcessingService paymentProcessingService;      // 결제 처리 서비스 (테스트 대상)
    private RetryRequestService retryRequestService;                // 재시도 요청 서비스 (테스트 대상)
    // 의존성 주입
    @Mock private PaymentGatewayService paymentGatewayService;       // PG 서비스 (모킹)
    @Mock private TransactionService transactionService;             // 거래 서비스 (모킹)
    @Mock private OrderRepository orderRepository;                   // 주문 리포지토리 (모킹)
    @Mock private DonationService donationService;                   // 후원 서비스 (모킹)
    @Mock private RetryRequestRepository retryRequestRepository;     // 재시도 리포지토리 (모킹)

    // JSON 처리용 ObjectMapper
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 각 테스트 전에 실행
    // 각 테스트 전에 실행되어 모킹된 의존성을 초기화하고 서비스 인스턴스를 생성
    @BeforeEach
    void setUp() {
        // PaymentProcessingService 인스턴스 생성
        paymentProcessingService = new PaymentProcessingService(
                paymentGatewayService,
                transactionService,
                orderRepository,
                donationService,
                retryRequestRepository,
                objectMapper
        );

        // RetryRequestService 인스턴스 생성
        retryRequestService = new RetryRequestService(
                retryRequestRepository,
                paymentProcessingService,
                objectMapper
        );
    }

    @Test
    @DisplayName("PG 결제 성공 시 DonationService.completeDirectDonation으로 위임한다")
    void createPayment_success() {
        // given
        // ConfirmRequest 생성
        ConfirmRequest confirmRequest = new ConfirmRequest(
                "paymentKey",
                "orderId",
                "1000"
        );

        // when
        // 결제 처리 서비스의 createPayment 메서드 호출
        paymentProcessingService.createPayment(confirmRequest);

        // then
        // DonationService.completeDirectDonation이 호출되었는지 검증
        then(donationService).should(times(1)).completeDirectDonation(confirmRequest);

        // 다른 의존성은 호출되지 않았는지 검증
        // 더 이상 내부에서 직접 수행하지 않음
        then(paymentGatewayService).shouldHaveNoInteractions();
        then(transactionService).shouldHaveNoInteractions();
        then(orderRepository).shouldHaveNoInteractions();

        // 디버깅 출력
        System.out.printf("✅ orderId=%s%n", confirmRequest.orderId());
    }

    @Test
    @DisplayName("PG 결제 충전 성공 시 충전 기록이 생성되고 주문이 APPROVED 된다")
    void createCharge_success() {
        // given
        // ConfirmRequest 생성
        ConfirmRequest confirmRequest = new ConfirmRequest(
                "paymentKey-123",
                "orderId-123",
                "5000" // 결제 금액 문자열
        );

        // ===== OrderRepository 스텁 설정 =====
        // Order 생성 및 초기 상태 설정
        Order order = new Order();
        order.setUserId(10L);                           // 사용자 ID 설정
        order.setRequestId(confirmRequest.orderId());   // 요청 ID 설정
        order.setStatus(OrderStatus.WAIT);              // 초기 상태 WAIT 설정
        order.setUpdatedAt(LocalDateTime.now());        // 초기 업데이트 시간 설정

        // orderRepository가 confirmRequest.orderId()로 호출될 때 order 객체를 반환하도록 스텁 설정
        given(orderRepository.findByRequestId(confirmRequest.orderId()))
                .willReturn(order);

        // ===== TransactionService 스텁 설정 =====
        // transactionService.charge() 호출 시 ChargeTransactionResponse 반환하도록 스텁 설정
        given(transactionService.charge(any(ChargeTransactionRequest.class)))
                .willReturn(new ChargeTransactionResponse(order.getUserId(), BigDecimal.valueOf(5000)));

        // when
        // 결제 처리 서비스의 createCharge 메서드 호출
        // isRetry: false (재시도 아님)
        paymentProcessingService.createCharge(confirmRequest, false);

        // then
        // 1. PG 승인 요청이 호출되었는지 확인
        // 1번 호출되었는지 검증
        then(paymentGatewayService).should(times(1)).confirm(confirmRequest);

        // 2. 트랜잭션 충전 기록이 생성되었는지 확인
        ArgumentCaptor<ChargeTransactionRequest> captor = ArgumentCaptor.forClass(ChargeTransactionRequest.class); // ArgumentCaptor 생성
        then(transactionService).should(times(1)).charge(captor.capture());                  // charge 메서드가 1번 호출되었는지 검증 및 인자 캡처

        // 캡처된 인자 검증
        ChargeTransactionRequest passedReq = captor.getValue();                                       // 캡처된 인자 가져오기
        assertThat(passedReq.walletId()).isEqualTo(order.getUserId());                                // userId 검증
        assertThat(passedReq.orderId()).isEqualTo(confirmRequest.orderId());                          // orderId 검증
        assertThat(passedReq.amount()).isEqualByComparingTo(new BigDecimal(confirmRequest.amount())); // amount 검증

        // 3. 주문 상태가 APPROVED 되었는지 확인
        // order 객체의 상태가 APPROVED인지 검증
        assertThat(order.getStatus()).isEqualTo(OrderStatus.APPROVED);

        // 4. 주문 저장이 호출되었는지 확인
        // 1번 호출되었는지 검증
        then(orderRepository).should(times(1)).save(order);

        // 디버깅 출력
        System.out.printf("✅ orderId=%s, userId=%d, status=%s%n",
                order.getRequestId(), order.getUserId(), order.getStatus());
    }

    // ⚠️ 이 테스트는 Timeout 상황을 모킹(Mock)하여 RetryRequest 저장 동작을 검증함.
    // 실제 네트워크 Timeout이 아니라 RestClientException(SocketTimeoutException) 을 강제로 발생시켜 테스트.
    @Test
    @DisplayName("Timeout 발생 시 RetryRequest 를 저장하고 예외를 다시 던진다")
    void createCharge_timeout_savesRetryAndThrows() {
        // given
        // ConfirmRequest 생성
        ConfirmRequest confirmRequest = new ConfirmRequest("paymentKey-1", "orderId-1", "1000");

        // ===== PaymentGatewayService 스텁 설정 =====
        // paymentGatewayService.confirm() 호출 시 payRestClientException(SocketTimeoutException) 예외를 발생시키도록 모킹
        RestClientException timeoutEx = new RestClientException("timeout", new SocketTimeoutException("Read timed out"));
        willThrow(timeoutEx).given(paymentGatewayService).confirm(confirmRequest);

        // when & then
        // 결제 처리 서비스의 createCharge 메서드 호출 시 RestClientException 예외가 발생하는지 검증
        // isRetry: false (재시도 아님)
        assertThatThrownBy(() -> paymentProcessingService.createCharge(confirmRequest, false))
                .isInstanceOf(RestClientException.class)
                .hasCauseInstanceOf(SocketTimeoutException.class);

        // 1. 호출 검증
        // PG 승인 요청이 1번 호출되었는지 확인
        then(paymentGatewayService).should(times(1)).confirm(confirmRequest);
        // 트랜잭션 충전 기록이 생성되지 않았는지 확인
        then(transactionService).should(never()).charge(any());
        // 주문 저장이 호출되지 않았는지 확인
        then(orderRepository).shouldHaveNoMoreInteractions();

        // 2. RetryRequest 저장 검증
        // RetryRequest 가 저장되는지 검증하기 위해 ArgumentCaptor 사용
        ArgumentCaptor<RetryRequest> captor = ArgumentCaptor.forClass(RetryRequest.class);
        // save 메서드가 1번 호출되었는지 검증 및 인자 캡처
        then(retryRequestRepository).should(times(1)).save(captor.capture());

        // 3. 저장된 RetryRequest 필드 값 검증
        // 캡처된 RetryRequest 검증
        RetryRequest saved = captor.getValue();
        // saved 객체의 필드 값 검증
        assertThat(saved.getRequestId()).isEqualTo(confirmRequest.orderId());
        // 요청 JSON이 confirmRequest 객체의 JSON 표현과 일치하는지 검증
        assertThat(saved.getType()).isEqualTo(RetryRequest.Type.CONFIRM);
        // 상태가 IN_PROGRESS 인지 검증
        assertThat(saved.getStatus()).isEqualTo(RetryRequest.Status.IN_PROGRESS);
        // 에러 응답 메시지에 "timeout" 문자열이 포함되어 있는지 검증
        assertThat(saved.getErrorResponse()).contains("timeout");

        // 디버깅 출력
        System.out.printf("✅ Saved RetryRequest: requestId=%s, type=%s, status=%s, errorResponse=%s%n",
                saved.getRequestId(), saved.getType(), saved.getStatus(), saved.getErrorResponse());
    }

    @Test
    @DisplayName("Timeout 후 Retry 성공 시 충전되고 RetryRequest 가 SUCCESS 로 변경된다")
    void retry_success_after_timeout() throws Exception {
        // given
        // ConfirmRequest 생성
        ConfirmRequest confirmRequest = new ConfirmRequest("paymentKey-1", "orderId-1", "5000");

        // 첫 호출 시 Timeout 예외 발생
        // ===== PaymentGatewayService 스텁 설정 =====
        // paymentGatewayService.confirm() 호출 시 RestClientException(SocketTimeoutException) 예외를 발생시키도록 모킹
        RestClientException timeoutEx =
                new RestClientException("timeout", new SocketTimeoutException("Read timed out"));
        willThrow(timeoutEx).given(paymentGatewayService).confirm(confirmRequest);

        // ===== OrderRepository 스텁 설정 =====
        // Order 생성 및 초기 상태 설정
        Order order = new Order();
        order.setUserId(10L);
        order.setRequestId(confirmRequest.orderId());
        order.setStatus(OrderStatus.WAIT);
        order.setUpdatedAt(LocalDateTime.now());

        // orderRepository가 confirmRequest.orderId()로 호출될 때 order 객체를 반환하도록 스텁 설정
        lenient().when(orderRepository.findByRequestId(confirmRequest.orderId())).thenReturn(order); // lenient() 사용하여 불필요한 stubbing 방지

        // 첫 호출: Timeout 예외 발생
        try {
            paymentProcessingService.createCharge(confirmRequest, false);
        } catch (RestClientException e) {
            // 예외는 무시하고 진행
        }

        // 두 번째 호출에서는 정상적으로 처리되도록 설정
        // 두 번째 호출: 정상 응답 반환
        // ===== TransactionService 스텁 설정 =====
        // transactionService.charge() 호출 시 ChargeTransactionResponse 반환하도록 스텁 설정
        given(transactionService.charge(any(ChargeTransactionRequest.class)))
                .willReturn(new ChargeTransactionResponse(order.getUserId(), BigDecimal.valueOf(5000)));

        // ===== RetryRequest 저장 검증 =====
        // RetryRequest 가 저장되는지 검증하기 위해 ArgumentCaptor 사용
        ArgumentCaptor<RetryRequest> captor = ArgumentCaptor.forClass(RetryRequest.class);          // ArgumentCaptor 생성
        then(retryRequestRepository).should(times(1)).save(captor.capture()); // save 메서드가 1번 호출되었는지 검증 및 인자 캡처
        RetryRequest saved = captor.getValue();                                                     // 캡처된 RetryRequest 가져오기

        // ===== RetryRequestRepository 스텁 설정 =====
        // 저장된 RetryRequest 의 ID를 1L로 설정하고, retryRequestRepository.findById(1L) 호출 시 해당 객체 반환하도록 스텁 설정
        saved.setId(1L);
        given(retryRequestRepository.findById(1L)).willReturn(Optional.of(saved));

        // ===== PaymentGatewayService 스텁 재설정 =====
        // 두 번째 호출 시에는 정상적으로 처리되도록 paymentGatewayService.confirm() 스텁 설정
        // 즉, 두 번째 호출에서는 아무 예외도 발생하지 않도록 설정
        willDoNothing().given(paymentGatewayService).confirm(confirmRequest);

        // when
        // RetryRequestService 의 retry 메서드 호출
        retryRequestService.retry(1L);

        // then
        // 1. RetryRequest 가 SUCCESS 로 변경되었는지 검증
        // save 메서드가 총 2번 호출되었는지 검증 (처음 저장 + 업데이트)
        then(retryRequestRepository).should(times(2)).save(captor.capture()); // save 메서드가 2번 호출되었는지 검증 및 인자 캡처
        RetryRequest updated = captor.getValue();                                                   // 마지막에 저장된 RetryRequest 가져오기
        assertThat(updated.getStatus()).isEqualTo(RetryRequest.Status.SUCCESS);                     // 상태가 SUCCESS 인지 검증

        // 2. PG 승인 요청이 총 2번 호출되었는지 검증 (처음 시도 + 재시도)
        then(paymentGatewayService).should(times(2)).confirm(confirmRequest);

        // 3. 트랜잭션 충전 기록이 1번 생성되었는지 검증
        then(transactionService).should(times(1)).charge(any(ChargeTransactionRequest.class));

        // 4. 주문 상태가 APPROVED 되었는지 검증
        assertThat(order.getStatus()).isEqualTo(OrderStatus.APPROVED);

        // 5. 주문 저장이 1번 호출되었는지 검증
        then(orderRepository).should(times(1)).save(order);

        // 디버깅 출력
        System.out.printf("✅ After Retry: orderId=%s, userId=%d, status=%s, retryStatus=%s%n",
                order.getRequestId(), order.getUserId(), order.getStatus(), updated.getStatus());
    }

}

package com.projectboard.payment;

import com.projectboard.payment.checkout.ConfirmRequest;
import com.projectboard.payment.external.PaymentGatewayService;
import com.projectboard.payment.order.Order;
import com.projectboard.payment.order.OrderRepository;
import com.projectboard.payment.order.OrderStatus;
import com.projectboard.payment.processing.PaymentProcessingService;
import com.projectboard.payment.transaction.ChargeTransactionResponse;
import com.projectboard.payment.transaction.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

public class PaymentProcessingServiceTest {

    // 테스트 결과 로거: 성공/실패/중단/비활성 상태 콘솔 출력
    @RegisterExtension
    static TestWatcher logWatcher = new TestWatcher() {
        @Override
        public void testSuccessful(ExtensionContext context) {
            System.out.println("✅ PASSED: " + context.getDisplayName());
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            System.err.println("❌ FAILED: " + context.getDisplayName());
            cause.printStackTrace(); // 빨간 스택트레이스 출력
        }

        @Override
        public void testAborted(ExtensionContext context, Throwable cause) {
            System.err.println("⚠️ ABORTED: " + context.getDisplayName());
        }

        @Override
        public void testDisabled(ExtensionContext context, Optional<String> reason) {
            System.out.println("⏸ DISABLED: " + context.getDisplayName() +
                    reason.map(r -> " — " + r).orElse(""));
        }
    };

    // System under test
    private PaymentProcessingService paymentProcessingService; // 테스트 대상 서비스

    // Mocked dependencies
    private PaymentGatewayService paymentGatewayService;       // PG 서비스 (모킹)
    private TransactionService transactionService;             // 거래 서비스 (모킹)
    private OrderRepository orderRepository;                   // 주문 리포지토리 (모킹)

    @BeforeEach
    // 각 테스트 전에 실행되어 모킹된 의존성을 초기화하고 서비스 인스턴스를 생성
    void setUp() {
        paymentGatewayService = mock(PaymentGatewayService.class); // PG 서비스 모킹
        transactionService = mock(TransactionService.class);       // 거래 서비스 모킹
        orderRepository = mock(OrderRepository.class);             // 주문 리포지토리 모킹

        // PaymentProcessingService 인스턴스 생성
        paymentProcessingService = new PaymentProcessingService(
                paymentGatewayService, transactionService, orderRepository // 모킹된 의존성 주입
        );
    }

    @Test
    @DisplayName("PG 결제 성공 시 결제 기록이 생성되고 주문이 APPROVED 된다")
    void createPayment_success() {
        // given
        // ConfirmRequest 생성
        ConfirmRequest confirmRequest = new ConfirmRequest(
                "paymentKey",
                "orderId",
                "1000"
        );

        // OrderRepository가 특정 주문을 반환하도록 설정
        Order order = new Order();               // 새로운 주문 객체 생성
        order.setUserId(123L);                   // 사용자 ID 설정
        order.setRequestId("orderId");           // 주문 ID 설정
        order.setStatus(OrderStatus.REQUESTED);  // 초기 상태 설정
        order.setUpdatedAt(LocalDateTime.now()); // 초기 업데이트 시간 설정

        // orderRepository가 confirmRequest.orderId()로 호출될 때 order 객체를 반환하도록 스텁 설정
        given(orderRepository.findByRequestId(confirmRequest.orderId()))
                .willReturn(order);

        // transactionService.pgPayment()가 성공적으로 반환하도록 스텁 설정
        given(transactionService.pgPayment(any(Long.class), any(String.class), any(BigDecimal.class)))
                .willReturn(new ChargeTransactionResponse(123L, new BigDecimal("1000")));

        // when
        // 결제 처리 서비스의 createPayment 메서드 호출
        paymentProcessingService.createPayment(confirmRequest);

        // then
        // PG 승인 요청 확인
        then(paymentGatewayService).should(times(1)).confirm(confirmRequest);

        // 결제 기록 저장 확인 - 새로운 시그니처로 호출되는지 확인
        then(transactionService).should(times(1))
                .pgPayment(order.getUserId(), order.getRequestId(), new BigDecimal("1000"));

        // 주문 상태가 APPROVED 로 변경되었는지 확인
        assertThat(order.getStatus()).isEqualTo(OrderStatus.APPROVED);

        // 주문 저장이 호출되었는지 확인
        then(orderRepository).should(times(1)).save(order);

        // 디버깅 출력
        System.out.println("Updated Order Status: " + order.getStatus());
        System.out.println("Transaction service called with userId: " + order.getUserId() 
                + ", orderId: " + order.getRequestId() + ", amount: 1000");
    }
}

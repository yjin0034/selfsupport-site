package com.projectboard.payment;

import com.projectboard.payment.checkout.ConfirmRequest;
import com.projectboard.payment.order.Order;
import com.projectboard.payment.order.OrderRepository;
import com.projectboard.payment.order.OrderStatus;
import com.projectboard.payment.processing.PaymentProcessingService;
import com.projectboard.payment.transaction.TransactionService;
import com.projectboard.payment.external.PaymentGatewayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ErrorHandlingVerificationTest {

    @Mock PaymentGatewayService paymentGatewayService;
    @Mock TransactionService transactionService;
    @Mock OrderRepository orderRepository;

    @InjectMocks PaymentProcessingService paymentProcessingService;

    @Test
    @DisplayName("주문을 찾을 수 없는 경우 예외 처리")
    void orderNotFound_throwsException() {
        // given
        ConfirmRequest confirmRequest = new ConfirmRequest(
                "payment_key_123", "non-existent-order", "1000"
        );
        
        given(orderRepository.findByRequestId("non-existent-order"))
                .willReturn(null);

        // when
        Throwable thrown = catchThrowable(() -> 
                paymentProcessingService.createPayment(confirmRequest));

        // then
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("주문을 찾을 수 없습니다");

        // PG 승인이나 충전 처리가 호출되지 않았는지 확인
        then(paymentGatewayService).should(never()).confirm(any(ConfirmRequest.class));
        then(transactionService).should(never()).pgPayment(any(Long.class), any(String.class), any(BigDecimal.class));

        System.out.println("✅ 주문 없음 예외 처리 검증 완료");
    }

    @Test
    @DisplayName("잘못된 금액 형식 예외 처리")
    void invalidAmountFormat_throwsException() {
        // given
        String orderId = "valid-order-123";
        Order validOrder = new Order();
        validOrder.setUserId(100L);
        validOrder.setRequestId(orderId);
        validOrder.setStatus(OrderStatus.REQUESTED);

        given(orderRepository.findByRequestId(orderId)).willReturn(validOrder);

        ConfirmRequest invalidAmountRequest = new ConfirmRequest(
                "payment_key_123", orderId, "invalid-amount-format"
        );

        // when
        Throwable thrown = catchThrowable(() -> 
                paymentProcessingService.createPayment(invalidAmountRequest));

        // then
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 금액 형식");

        // PG 승인은 성공하지만 충전 처리는 호출되지 않음
        then(paymentGatewayService).should(times(1)).confirm(invalidAmountRequest);
        then(transactionService).should(never()).pgPayment(any(Long.class), any(String.class), any(BigDecimal.class));

        System.out.println("✅ 잘못된 금액 형식 예외 처리 검증 완료");
    }

    @Test
    @DisplayName("PG 승인 실패 시 예외 전파")
    void pgApprovalFailure_propagatesException() {
        // given
        String orderId = "valid-order-456";
        Order validOrder = new Order();
        validOrder.setUserId(200L);
        validOrder.setRequestId(orderId);
        validOrder.setStatus(OrderStatus.REQUESTED);

        given(orderRepository.findByRequestId(orderId)).willReturn(validOrder);

        ConfirmRequest confirmRequest = new ConfirmRequest(
                "invalid_payment_key", orderId, "5000"
        );

        // PG 승인이 실패하도록 설정
        doThrow(new RuntimeException("PG 승인 실패"))
                .when(paymentGatewayService).confirm(confirmRequest);

        // when
        Throwable thrown = catchThrowable(() -> 
                paymentProcessingService.createPayment(confirmRequest));

        // then
        assertThat(thrown)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PG 승인 실패");

        // PG 승인만 호출되고 충전 처리는 호출되지 않음
        then(paymentGatewayService).should(times(1)).confirm(confirmRequest);
        then(transactionService).should(never()).pgPayment(any(Long.class), any(String.class), any(BigDecimal.class));

        System.out.println("✅ PG 승인 실패 예외 전파 검증 완료");
    }

    @Test
    @DisplayName("충전 처리 실패 시 예외 전파")
    void chargingFailure_propagatesException() {
        // given
        String orderId = "valid-order-789";
        Long userId = 300L;
        BigDecimal amount = new BigDecimal("2000");
        
        Order validOrder = new Order();
        validOrder.setUserId(userId);
        validOrder.setRequestId(orderId);
        validOrder.setStatus(OrderStatus.REQUESTED);

        given(orderRepository.findByRequestId(orderId)).willReturn(validOrder);

        ConfirmRequest confirmRequest = new ConfirmRequest(
                "payment_key_789", orderId, amount.toString()
        );

        // 충전 처리가 실패하도록 설정 (예: 지갑이 없는 경우)
        given(transactionService.pgPayment(userId, orderId, amount))
                .willThrow(new RuntimeException("지갑을 찾을 수 없습니다"));

        // when
        Throwable thrown = catchThrowable(() -> 
                paymentProcessingService.createPayment(confirmRequest));

        // then
        assertThat(thrown)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("지갑을 찾을 수 없습니다");

        // PG 승인과 충전 처리 모두 호출되었는지 확인
        then(paymentGatewayService).should(times(1)).confirm(confirmRequest);
        then(transactionService).should(times(1)).pgPayment(userId, orderId, amount);

        // 주문 상태는 변경되지 않음 (예외로 인해 롤백)
        then(orderRepository).should(never()).save(validOrder);

        System.out.println("✅ 충전 처리 실패 예외 전파 검증 완료");
    }

    @Test
    @DisplayName("에러 처리 시나리오 요약")
    void errorHandlingSummary() {
        System.out.println("🛡️ 구현된 에러 처리 시나리오:");
        System.out.println();
        
        System.out.println("1️⃣ 주문 조회 실패");
        System.out.println("   - orderId에 해당하는 주문이 없는 경우");
        System.out.println("   - IllegalArgumentException with 명확한 메시지");
        System.out.println("   - 후속 처리 중단");
        System.out.println();
        
        System.out.println("2️⃣ 입력 데이터 검증 실패");
        System.out.println("   - 금액 형식이 잘못된 경우 (NumberFormatException)");
        System.out.println("   - TransactionService에서 추가 유효성 검증");
        System.out.println("   - IllegalArgumentException 변환");
        System.out.println();
        
        System.out.println("3️⃣ PG 승인 실패");
        System.out.println("   - PaymentGatewayService에서 예외 발생");
        System.out.println("   - 예외 전파로 트랜잭션 롤백");
        System.out.println();
        
        System.out.println("4️⃣ 충전 처리 실패");
        System.out.println("   - 지갑이 없거나 충전 한도 초과 등");
        System.out.println("   - 기존 charge() 메서드의 검증 로직 활용");
        System.out.println("   - 예외 전파로 트랜잭션 롤백");
        System.out.println();
        
        System.out.println("5️⃣ CheckoutController 레벨 처리");
        System.out.println("   - 모든 예외를 포괄적으로 캐치");
        System.out.println("   - 주문 상태를 FAIL로 변경");
        System.out.println("   - 적절한 HTTP 응답 코드 반환");
        System.out.println("   - 상세한 로깅");
    }
}
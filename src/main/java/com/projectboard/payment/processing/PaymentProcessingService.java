package com.projectboard.payment.processing;

import com.projectboard.payment.checkout.ConfirmRequest;
import com.projectboard.payment.external.PaymentGatewayService;
import com.projectboard.payment.order.Order;
import com.projectboard.payment.order.OrderRepository;
import com.projectboard.payment.order.OrderStatus;
import com.projectboard.payment.transaction.TransactionService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 처리 서비스
 * - 결제 승인 요청 및 결제 기록 저장을 담당.
 * - 주문 상태 업데이트 및 후원 내역 저장.
 */
@Service
@AllArgsConstructor
public class PaymentProcessingService {
    private final PaymentGatewayService paymentGatewayService;
    private final TransactionService transactionService;

    private final OrderRepository orderRepository;

    // ===== 결제 처리 =====
    // - 결제 승인 요청 및 결제 기록 저장
    // - 주문 상태 업데이트 및 후원 내역 저장
    public void createPayment(ConfirmRequest confirmRequest) {
       /*
       3. 결제 서비스 - PG 승인 요청
       4. 결제 서비스 - 결제 기록 저장
          - 결제 수단으로 바로 결제하는 메서드 구현
       ...
       6. 주문 서비스에 응답
       7. 주문 서비스 - 주문 상태가 변경됨 -> APPROVED
       8. 주문 서비스 - 후원 내역 저장
        */

        // 주문 정보 조회
        final Order order = orderRepository.findByRequestId(confirmRequest.orderId());
        if (order == null) {
            throw new IllegalArgumentException("주문을 찾을 수 없습니다: " + confirmRequest.orderId());
        }

        // PG 승인 요청
        paymentGatewayService.confirm(confirmRequest);

        // 결제 기록 저장 및 지갑 충전 처리
        // confirmRequest.amount()를 BigDecimal로 변환
        BigDecimal amount;
        try {
            amount = new BigDecimal(confirmRequest.amount());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("유효하지 않은 금액 형식: " + confirmRequest.amount(), e);
        }

        // PG 결제를 통한 충전 처리
        transactionService.pgPayment(order.getUserId(), order.getRequestId(), amount);

        // 주문 상태 변경
        order.setStatus(OrderStatus.APPROVED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }
}

package com.projectboard.payment.processing;

import com.projectboard.payment.checkout.ConfirmRequest;
import com.projectboard.payment.external.PaymentGatewayService;
import com.projectboard.payment.order.Order;
import com.projectboard.payment.order.OrderRepository;
import com.projectboard.payment.order.OrderStatus;
import com.projectboard.payment.transaction.ChargeTransactionRequest;
import com.projectboard.payment.transaction.TransactionService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 처리 서비스
 * - 결제 승인 요청 및 결제 기록 저장
 * - 충전 승인 요청 및 충전 기록 저장
 * - 주문 상태 업데이트 및 내역 저장
 * - 주문 서비스와 연동
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
        /**
         3. 결제 서비스 - PG 승인 요청
         4. 결제 서비스 - 결제 기록 저장
            - 결제 수단으로 바로 결제하는 메서드 구현
         ...
         6. 주문 서비스에 응답
         7. 주문 서비스 - 주문 상태가 변경됨 -> APPROVED
         8. 주문 서비스 - 결제 내역 저장
         */

        // PG 승인 요청
        paymentGatewayService.confirm(confirmRequest);

        // FIXME: pgPayment() 메서드 내에서 결제 기록 저장 로직 구현 필요
        // 결제 기록 저장
        transactionService.pgPayment();

        // 주문 상태 변경
        approveOrder(confirmRequest.orderId());
    }

    // ===== 충전 처리 =====
    // - 결제 승인 요청 및 충전 기록 저장
    // - 주문 상태 업데이트 및 충전 내역 저장
    public void createCharge(ConfirmRequest confirmRequest) {
        /**
         .. 주문서비스 프로세스
         3. 결제 서비스 - PG 승인 요청
         4. 결제 서비스 - 충전기록 저장
            -> 결제수단 -> 잔액 충전 구현
         ...
         6. 주문 서비스에 응답
         7. 주문 서비스 - 주문 상태가 변경됨 -> APPROVED
         8. 주문 서비스 - 충전 내역 저장
         */

        // PG 승인 요청
        paymentGatewayService.confirm(confirmRequest);

        /**
         * FIXME: 현재는 PG에서 승인으로 넘어오는 요청을 바로 호출하고 있다.
         *   실제 프로세스에서는 주문 서비스를 통해서 Order의 OrdrerId로
         *   내 테이블의 UserId를 매핑해 가져오도록 해야 한다.
         *   이후 그 과정을 구현하기.
         */
        // 주문 정보 조회
        final Order order = orderRepository.findByRequestId(confirmRequest.orderId());

        // 충전 기록 저장
        transactionService.charge(
            new ChargeTransactionRequest(
                    order.getUserId(),
                    confirmRequest.orderId(),
                    new BigDecimal(confirmRequest.amount())
            )
        );

        // 주문 상태 변경
        approveOrder(confirmRequest.orderId());
    }

    // ===== 주문 상태 변경 =====
    // - 주문 상태를 APPROVED로 변경하고 수정 시간 업데이트
    private void approveOrder(String orderId) {
        final Order order = orderRepository.findByRequestId(orderId); // 주문 조회
        order.setStatus(OrderStatus.APPROVED);                        // 주문 상태 변경
        order.setUpdatedAt(LocalDateTime.now());                      // 주문 수정 시간 업데이트
        orderRepository.save(order);                                  // 변경 사항 저장
    }

}

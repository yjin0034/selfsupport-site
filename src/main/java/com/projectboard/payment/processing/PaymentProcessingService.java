package com.projectboard.payment.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectboard.payment.checkout.ConfirmRequest;
import com.projectboard.payment.external.PaymentGatewayService;
import com.projectboard.payment.order.Order;
import com.projectboard.payment.order.OrderRepository;
import com.projectboard.payment.order.OrderStatus;
import com.projectboard.payment.retry.RetryRequestRepository;
import com.projectboard.payment.retry.RetryRequest;
import com.projectboard.payment.transaction.ChargeTransactionRequest;
import com.projectboard.payment.transaction.TransactionService;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;

/**
 * 결제 처리 서비스
 * - 결제 승인 요청 및 결제 기록 저장
 * - 충전 승인 요청 및 충전 기록 저장
 * - 주문 상태 업데이트 및 내역 저장
 * - 주문 서비스와 연동
 */
@Slf4j
@Service
@AllArgsConstructor
public class PaymentProcessingService {
    // ===== 의존성 주입 =====
    private final PaymentGatewayService paymentGatewayService;  // 외부 결제 게이트웨이 서비스
    private final TransactionService transactionService;        // 거래 기록 서비스

    private final OrderRepository orderRepository;              // 주문 리포지토리

    private final RetryRequestRepository retryRepository;       // 재시도 요청 리포지토리

    private ObjectMapper objectMapper;                          // JSON 객체 매퍼

    /**
     * 결제 생성
     * - 결제 승인 요청 및 결제 기록 저장
     * - 주문 상태 업데이트 및 후원 내역 저장
     *
     * @param confirmRequest 결제 승인 요청 정보
     */
    public void createPayment(ConfirmRequest confirmRequest) {
        // PG 승인 요청
        paymentGatewayService.confirm(confirmRequest);

        // 결제 기록 저장
        transactionService.pgPayment(
                    null,
                    confirmRequest.orderId(),
                    new BigDecimal(confirmRequest.amount()),
                    confirmRequest.paymentKey()
        );

        // 주문 상태 변경
        approveOrder(confirmRequest.orderId());
    }

    /**
     * 충전 생성
     * - 결제 승인 요청 및 충전 기록 저장
     * - 주문 상태 업데이트 및 충전 내역 저장
     *
     * @param confirmRequest 결제 승인 요청 정보
     * @param isRetry 재시도 여부 (true: 재시도, false: 최초 시도)
     */
    public void createCharge(ConfirmRequest confirmRequest, boolean isRetry) {
        // 예외 처리
        // - PG 승인 요청 중 예외 발생 시 재시도 요청 생성
        try {
            // PG 승인 요청
            paymentGatewayService.confirm(confirmRequest);

        } catch (Exception e){
            // 예외 로그 기록
            log.error("createCharge 중 예외 발생", e);

            // TODO: 재시도 데이터 저장

            // isRetry 가 false 이고,
            // RestClientException 의 원인이 SocketTimeoutException 인 경우에만 재시도 요청 생성
            if (!isRetry && e instanceof RestClientException &&
                    e.getCause() instanceof SocketTimeoutException) {
                // 재시도 요청 생성
                createRetryRequest(confirmRequest, e);
            }

            // 예외 다시 던지기
            throw e;
        }

        /**
         * FIXME: 현재는 PG에서 승인으로 넘어오는 요청을 바로 호출하고 있다.
         *   실제 프로세스에서는 주문 서비스를 통해서 Order의 OrdrerId로
         *   내 테이블의 UserId를 매핑해 가져오도록 해야 한다.
         *   이후 그 과정을 구현하기.
         */
        // 주문 정보 조회
        final Order order = orderRepository.findByRequestId(confirmRequest.orderId());

        // 충전 기록 저장
        // - 주문의 UserId와 ConfirmRequest의 amount를 사용하여 충전 요청
        transactionService.charge(                          // 충전 기록 저장
            new ChargeTransactionRequest(                   // 충전 요청 객체 생성
                    order.getUserId(),                      // 주문의 UserId
                    confirmRequest.orderId(),               // 주문 ID
                    new BigDecimal(confirmRequest.amount()) // 충전 금액 (BigDecimal 변환)
            )
        );

        // 주문 상태 변경
        approveOrder(confirmRequest.orderId());
    }

    /**
     * 재시도 요청 생성
     * - 결제 승인 요청 실패 시, 재시도 요청을 생성하여 저장
     * - 예외 메시지와 요청 데이터를 함께 저장
     *
     * @param confirmRequest
     * @param e
     */
    @SneakyThrows
    private void createRetryRequest(ConfirmRequest confirmRequest, Exception e) {
        // 재시도 요청 객체 생성
        RetryRequest retryRequest = new RetryRequest(
                objectMapper.writeValueAsString(confirmRequest),    // 요청 JSON 직렬화
                confirmRequest.orderId(),                           // 주문 ID
                e.getMessage(),                                     // 예외 메시지
                RetryRequest.Type.CONFIRM                           // 재시도 요청 타입: CONFIRM (결제 승인 요청)
        );

        // 재시도 요청 저장
        retryRepository.save(retryRequest);
    }

    /**
     * 주문 승인 처리
     * - 주문 상태를 APPROVED로 변경하고 수정 시간 업데이트
     *
     * @param orderId 주문 ID
     */
    private void approveOrder(String orderId) {
        final Order order = orderRepository.findByRequestId(orderId); // 주문 조회
        order.setStatus(OrderStatus.APPROVED);                        // 주문 상태 변경
        order.setUpdatedAt(LocalDateTime.now());                      // 주문 수정 시간 업데이트
        orderRepository.save(order);                                  // 변경 사항 저장
    }

}

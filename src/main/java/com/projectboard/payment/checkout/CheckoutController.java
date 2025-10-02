package com.projectboard.payment.checkout;

import com.projectboard.payment.order.Order;
import com.projectboard.payment.order.OrderRepository;
import com.projectboard.payment.processing.PaymentProcessingService;
import com.projectboard.payment.order.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * CheckoutController
 * - 결제 관련 페이지 렌더링 및 결제 승인 API 제공
 * - 주문 생성, 결제 승인, 결제 실패 페이지 등 처리
 */
@Slf4j
@Controller
@AllArgsConstructor
public class CheckoutController {
    // ===== 의존성 주입 =====
    private final OrderRepository orderRepository;                      // 주문 리포지토리
    private final PaymentProcessingService paymentProcessingService;    // 결제 처리 서비스

    /**
     * 주문 페이지
     * - 사용자가 결제할 금액과 주문 ID를 확인하는 화면.
     * - 멱등성 보장을 위한 requestId 처리 포함.
     * - templates/payment/order.html 뷰를 렌더링.
     *
     * @param userId        사용자 ID
     * @param amountQS     결제 금액 (문자열)
     * @param donationId    후원 ID
     * @param donationName  후원 이름
     * @param requestIdFromQS 멱등성 보장을 위한 requestId (선택적)
     * @param model         뷰 모델
     * @return 주문 페이지 뷰 이름
     */
    @GetMapping("/order")
    public String order(
            @RequestParam("userId") Long userId,
            @RequestParam("amount") String amountQS,
            @RequestParam("donationId") Long donationId,
            @RequestParam("donationName") String donationName,
            @RequestParam(value = "requestId", required = false) String requestIdFromQS,
            Model model
    ) {
        // 1. 주문 조회 또는 생성
        // Order 객체 선언
        final Order order;

        // 1) requestId가 있으면 해당 주문 조회
        // 멱등성 보장을 위한 requestId 처리
        if (StringUtils.hasText(requestIdFromQS)) {
            // requestId로 주문 조회
            order = Optional.ofNullable(orderRepository.findByRequestId(requestIdFromQS))                   // 주문 조회
                    .orElseThrow(() -> new ResponseStatusException(                                         // 주문이 존재하지 않으면 예외 발생
                            HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다. requestId=" + requestIdFromQS));    // 404 Not Found 응답

            // 주문의 userId와 요청한 userId가 일치하는지 확인
            if (!order.getUserId().equals(userId)) {
                // 일치하지 않으면 403 Forbidden 응답
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "주문 소유자가 일치하지 않습니다.");
            }
        }
        // 2) requestId가 없으면 새 주문 생성
        else {
            // amount 파싱
            BigDecimal amount;
            // amountQS가 올바른 형식인지 확인
            try {
                // BigDecimal로 변환 시도
                amount = new BigDecimal(amountQS);
            } catch (NumberFormatException e) { // 변환 실패 시 예외 처리
                // 잘못된 형식 예외 발생
                // 400 Bad Request 응답
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount 형식이 올바르지 않습니다.");
            }

            // 새 주문 생성
            order = new Order();
            order.setAmount(amount);                                                        // 주문 금액 설정
            order.setUserId(userId);                                                        // 사용자 ID 설정
            order.setRequestId(java.util.UUID.randomUUID().toString());                     // 멱등성 보장을 위한 requestId 생성
            order.waitStatus();                                                             // 주문 상태를 WAIT로 설정
            orderRepository.save(order);                                                    // 주문 저장
        }

        // 2. 모델에 주문 정보 추가
        model.addAttribute("donationName", donationName);                       // 후원 이름
        model.addAttribute("requestId", order.getRequestId());                  // 주문 요청 ID
        model.addAttribute("amount", order.getAmount().toPlainString());        // 주문 금액
        model.addAttribute("customerKey", "customerKey-" + userId);  // 고객 키 (예: "customerKey-1")

        // 3. 주문 페이지 뷰 렌더링
        // templates/payment/order.html
        return "payment/order";
    }

    /**
     * 주문 요청 완료 페이지
     * - 사용자가 결제를 요청한 후 보여지는 페이지.
     * - templates/payment/order-requested.html 뷰를 렌더링.
     *
     * @return 주문 요청 완료 페이지 뷰 이름
     */
    @GetMapping("/order-requested")
    public String orderRequested() {
        return "payment/order-requested";
    }

    /**
     * 결제 실패 페이지
     * - Toss 결제 실패 후 리다이렉트될 URL
     * - templates/payment/fail.html 뷰를 렌더링
     *
     * @return 결제 실패 페이지 뷰 이름
     */
    @GetMapping("/fail")
    public String fail() {
        return "payment/fail";
    }

    /**
     * 결제 승인 API
     * - 사용자가 결제를 승인할 때 호출되는 엔드포인트.
     * - 주문 상태를 REQUESTED로 변경하고, 결제 승인 요청을 처리.
     *
     * @param confirmRequest 결제 승인 요청 데이터
     * @return 성공 시 200 OK 응답
     * @throws Exception 결제 처리 중 예외 발생 시
     */
    @RequestMapping(method = RequestMethod.POST, value = "/confirm")
    public ResponseEntity<Object> confirmPayment(@RequestBody ConfirmRequest confirmRequest) throws Exception {
        // 1. 주문 상태 변경 (WAIT -> REQUESTED)
        Order order = orderRepository.findByRequestId(confirmRequest.orderId());
        order.setUpdatedAt(LocalDateTime.now());
        order.setStatus(OrderStatus.REQUESTED);
        orderRepository.save(order);

        // 2. 결제 승인 요청
        paymentProcessingService.createPayment(confirmRequest);

        // 3. 주문 서비스에 응답
        return ResponseEntity.ok(null);
    }

}

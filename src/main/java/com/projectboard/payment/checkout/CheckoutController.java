package com.projectboard.payment.checkout;

import com.projectboard.payment.order.Order;
import com.projectboard.payment.order.OrderRepository;
import com.projectboard.payment.processing.PaymentProcessingService;
import com.projectboard.payment.order.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
     * 주문 생성 페이지
     * - 사용자가 결제할 금액과 주문 ID를 확인하는 화면.
     * - templates/payment/order.html 뷰를 렌더링.
     */
    @GetMapping("/order")
    public String order(
            @RequestParam("userId") Long userId,
            @RequestParam("amount") String amount,
            @RequestParam("donationId") Long donationId,
            @RequestParam("donationName") String donationName,
            @RequestParam(value = "requestId", required = false) String requestIdFromQS,
            Model model
    ) {
        Order order;
        if (requestIdFromQS != null) {
            order = orderRepository.findByRequestId(requestIdFromQS);
        } else {
            order = new Order();
            order.setAmount(new BigDecimal(amount));
            order.setUserId(userId);
            order.setRequestId(java.util.UUID.randomUUID().toString());
            order.waitStatus();
            orderRepository.save(order);
        }

        // 모델에 주문 정보 추가
        model.addAttribute("donationName", donationName);                       // 후원 아이템 이름
        model.addAttribute("requestId", order.getRequestId());                  // 고유 주문 ID
        model.addAttribute("amount", amount);                                   // 후원 금액
        model.addAttribute("customerKey", "customerKey-" + userId); // 고객 키 (예: 사용자 ID 기반)

        // 주문 페이지 뷰 이름 반환
        // templates/payment/order.html
        return "payment/order";
    }

    /**
     * 주문 요청 페이지
     * - 사용자가 결제할 금액과 주문 ID를 확인하는 화면.
     * - templates/payment/order-requested.html 뷰를 렌더링.
     */
    @GetMapping("/order-requested")
    public String orderRequested() {
        return "payment/order-requested";
    }

    /**
     * 결제 실패 페이지
     * - Toss 결제 실패 후 redirect 될 URL
     * - templates/payment/fail.html 렌더링
     */
    @GetMapping("/fail")
    public String fail() {
        return "payment/fail";
    }

    /**
     * 결제 승인 API 호출
     * - 프론트엔드(success.html)에서 결제 성공 시, toss에서 받은 결제 정보를 서버로 전달함.
     * - 서버는 이 데이터를 Toss Payments API로 전달하여 결제를 "승인(confirm)"함.
     * - 이 과정을 통해 결제가 최종적으로 확정되고, DB에 내역을 저장할 수 있음.
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

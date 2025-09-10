package com.projectboard.payment.checkout;

import com.projectboard.payment.order.Order;
import com.projectboard.payment.order.OrderRepository;
import com.projectboard.payment.order.OrderStatus;
import com.projectboard.payment.processing.PaymentProcessingService;
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
 * 충전 컨트롤러
 * - 주문 생성, 결제 페이지 진입, 결제 승인 요청 처리 기능 제공.
 * - 주문 상태 관리 및 결제 처리 서비스 연동.
 */
@Slf4j
@Controller
@AllArgsConstructor
public class ChargeController {

    private final OrderRepository orderRepository;
    private final PaymentProcessingService paymentProcessingService;

    /**
     * 주문 생성 및 결제 페이지 진입
     * - 사용자가 결제를 시작할 때 호출되는 엔드포인트.
     * - 새로운 주문을 생성하고, 결제 페이지로 리디렉션.
     * - templates/payment/charge-order.html 뷰를 렌더링.
     */
    @GetMapping("/charge-order")
    public String order(
            @RequestParam("userId") Long userId,
            @RequestParam("amount") String amount,
            Model model
    ) {
        Order order = new Order();
        order.setAmount(new BigDecimal(amount));
        order.setUserId(userId);
        order.setRequestId(UUID.randomUUID().toString());
        order.setStatus(OrderStatus.WAIT);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        model.addAttribute("requestId", order.getRequestId());
        model.addAttribute("amount", amount);
        model.addAttribute("customerKey", "customerKey-" + userId);

        return "payment/charge-order";
    }

    /**
     * 주문 요청 완료 페이지
     * - 사용자가 결제 요청을 완료한 후 리디렉션되는 페이지.
     * - templates/payment/charge-order-requested.html 뷰를 렌더링.
     */
    @GetMapping("/charge-order-requested")
    public String orderRequested() {
        return "payment/charge-order-requested";
    }

    /**
     * 결제 실패 페이지
     * - 결제 실패 시 사용자에게 알림 및 재시도 옵션 제공.
     * - templates/payment/charge-fail.html 뷰를 렌더링.
     */
    @GetMapping("/charge-fail")
    public String fail() {
        return "payment/charge-fail";
    }

    /**
     * 결제 승인 요청 처리
     * - Toss 결제 완료 후 호출되는 엔드포인트.
     * - 주문 상태를 REQUESTED로 변경하고, 충전 처리를 수행.
     * - 주문 서비스에 응답을 반환.
     */
    @RequestMapping(method = RequestMethod.POST, value = "/charge-confirm")
    public ResponseEntity<Object> confirm(@RequestBody ConfirmRequest confirmRequest) throws Exception {
        // 1. 주문 상태 변경 (WAIT -> REQUESTED)
        Order order = orderRepository.findByRequestId(confirmRequest.orderId());
        order.setUpdatedAt(LocalDateTime.now());
        order.setStatus(OrderStatus.REQUESTED);
        orderRepository.save(order);

        // 2. 결제 서비스 - 충전 처리
        paymentProcessingService.createCharge(confirmRequest);

        // 3. 주문 서비스에 응답
        return ResponseEntity.ok(null);
    }

}

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
 * 체크아웃 컨트롤러
 * - 주문 생성, 결제 페이지 진입, 결제 승인 처리 등을 담당.
 * - 결제 성공 및 실패 페이지 렌더링.
 */
@Slf4j
@Controller
@AllArgsConstructor
public class CheckoutController {
    /**
    TODO:
    1. checkout 페이지 렌더링 시 orderId 를 만들어줘야 한다.
        - orderId 는 고유해야 한다. (ex: UUID)
        - 결제 성공 후 주문 내역을 조회할 때 사용된다.

    2. PG 와 주고 받은 데이터를 저장
        - 요청 -> 승인
        - 요청 데이터 저장
        - 승인 데이터 저장

    3. 후원 결제 API 연동
        - 후원 결제 시, 후원자 정보 및 후원 금액을 함께 처리.
        - 후원 내역을 데이터베이스에 저장.

    4. 결제 내역 저장 및 관리 기능 구현 (선택 사항)
        - 결제 내역을 데이터베이스에 저장.
        - 사용자가 자신의 결제 내역을 조회할 수 있는 기능 추가.
        - 관리자가 결제 내역을 조회할 수 있는 기능 추가.

    5. 환불 처리 기능 구현 (선택 사항)
        - 환불 요청 시, 토스페이먼츠 환불 API 호출 구현.
        - 환불 내역을 데이터베이스에 저장 및 관리.
        - 환불 상태를 사용자에게 알림.

    6. UI/UX 개선 (선택 사항)
        - 후원 페이지 및 결제 페이지 디자인 개선.
        - 마이페이지에서 후원 및 결제 내역 확인 기능 추가.
        - 관리자 페이지에서 결제 및 환불 내역 관리 기능 추가.

    7. 에러 처리
        - API 호출 실패 시, 적절한 에러 메시지 반환.
        - 결제 실패 시, 사용자에게 알림 및 재시도 옵션 제공.
        - 로그를 통해 에러 원인 분석 및 추적 가능하도록 구현.

    8. 테스트 및 검증
        - 다양한 결제 시나리오에 대한 테스트 케이스 작성.
        - 실제 결제 환경에서의 검증.

    9. 보안 강화
        - 결제 관련 API 호출 시, 인증 및 권한 부여 구현.
        - 민감한 정보(예: 시크릿 키) 보호.
     */

    private final OrderRepository orderRepository;
    private final PaymentProcessingService paymentProcessingService;

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
            Model model
    ) {
        Order order = new Order();
        order.setAmount(new BigDecimal(amount));
        order.setDonationId(donationId);
        order.setDonationName(donationName);
        order.setUserId(userId);
        order.setRequestId(UUID.randomUUID().toString());
        order.setStatus(OrderStatus.WAIT);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        model.addAttribute("donationName", donationName);
        model.addAttribute("requestId", order.getRequestId());
        model.addAttribute("amount", amount);
        model.addAttribute("customerKey", "customerKey-" + userId);

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

package com.projectboard.payment.wallet;

import com.projectboard.payment.checkout.ConfirmRequest;
import com.projectboard.payment.order.Order;
import com.projectboard.payment.order.OrderRepository;
import com.projectboard.payment.order.OrderStatus;
import com.projectboard.payment.processing.PaymentProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * WalletController
 * - 지갑 관련 API를 제공하는 컨트롤러 클래스.
 * - 지갑 생성, 조회, 포인트 충전 주문 생성 및 결제 승인 처리 기능 포함.
 */
@RequiredArgsConstructor
@Controller
@RequestMapping("/api/wallets")
public class WalletController {
    // ===== 의존성 주입 =====
    private final WalletService walletService;                          // 지갑 서비스
    private final OrderRepository orderRepository;                      // 주문 리포지토리
    private final PaymentProcessingService paymentProcessingService;    // 결제 처리 서비스

    /**
     * 지갑 생성 API
     * - userId에 고유 제약 조건(Unique Constraint) 설정으로 멱등성 보장
     * - 이미 존재하는 userId로 생성 시, 기존 지갑 반환
     *
     * @param request 지갑 생성 요청 정보
     * @return 생성된 지갑 정보
     */
    @PostMapping()
    public CreatedWalletResponse createWallet(@RequestBody CreateWalletRequest request) {
        // 지갑 생성 서비스 호출
        return walletService.createWallet(request);
    }

    /**
     * 사용자 ID로 지갑 조회 API
     * - userId로 지갑 정보를 조회
     *
     * @param userId 사용자 ID
     * @return 조회된 지갑 정보
     */
    @GetMapping("/{userId}")
    @ResponseBody
    public FindWalletResponse getWallet(@PathVariable Long userId) {
        // 지갑 조회 서비스 호출
        return walletService.findWalletByWalletId(userId);
    }

    /**
     * 포인트 충전 주문 생성 및 결제 페이지 진입 API
     * - 새로운 주문을 생성하고, 결제 페이지 뷰로 연결
     *
     * @param userId 사용자 ID
     * @param amount 충전 금액
     * @param model  뷰에 전달할 모델 객체
     * @return 결제 페이지 뷰 이름
     */
    @GetMapping("/charge")
    public String createChargeOrder(
            @RequestParam("userId") Long userId,
            @RequestParam("amount") String amount,
            Model model
    ) {
        // 주문 생성
        // - 포인트 충전을 위한 주문 생성
        Order order = new Order();
        order.setAmount(new BigDecimal(amount));            // 충전 금액
        order.setUserId(userId);                            // 사용자 ID
        order.setRequestId(UUID.randomUUID().toString());   // 고유 주문 ID
        order.setStatus(OrderStatus.WAIT);                  // 주문 상태: 대기
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);                        // 주문 저장

        // 모델에 값 전달
        model.addAttribute("requestId", order.getRequestId());                  // 주문 요청 ID
        model.addAttribute("amount", amount);                                   // 충전 금액
        model.addAttribute("customerKey", "customerKey-" + userId);  // 고객 키

        // 이후 PG 결제 뷰(payment/charge-order.html)로 연결
        return "payment/charge-order";
    }

    /**
     * 포인트 충전 결제 승인 API
     * - PG사로부터 결제 승인 요청을 받아 처리
     *
     * @param confirmRequest 결제 승인 요청 정보
     * @return 성공 메시지
     */
    @PostMapping("/charge-confirm")
    public ResponseEntity<Object> confirmCharge(@RequestBody ConfirmRequest confirmRequest) {
        // 결제 처리 서비스 호출
        paymentProcessingService.createCharge(confirmRequest, false);

        // 성공 메시지 반환
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header("Location", "/donations") // 결제 완료 후 이동
                .build();
    }

    // FIXME: 아래 API는 테스트 용도로만 사용. 실제 운영 시 제거 필요.
    /**
     * 잔액 추가 API (테스트 용도)
     * - userId로 지갑을 조회하여 잔액을 추가
     * - 실제 운영 환경에서는 PG 결제 완료 후에만 잔액이 추가되도록 구현 필요
     *
     * @param request 잔액 추가 요청 정보
     * @return 잔액 추가 후의 지갑 정보
     */
    @PostMapping("/api/wallets/add-balance")
    public AddBalanceWalletResponse addBalance(@RequestBody AddBalanceWalletRequest request) {
        // 잔액 추가 서비스 호출
        return walletService.addBalance(request);
    }
}

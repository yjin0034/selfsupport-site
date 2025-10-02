package com.projectboard.payment.donation;

import com.projectboard.payment.checkout.ConfirmRequest;
import com.projectboard.payment.processing.PaymentProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DonationController
 * - 후원 관련 API 엔드포인트를 제공하는 컨트롤러 클래스.
 * - 포인트 후원, 직접 결제 후원, 내 후원 내역 조회 기능 포함.
 * - 각 API는 DonationService를 호출하여 비즈니스 로직 처리.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/api/donations")
public class DonationController {
    // ===== 의존성 주입 =====
    private final DonationService donationService;                      // 후원 서비스
    private final PaymentProcessingService paymentProcessingService;    // 결제 처리 서비스

    /**
     * 포인트 후원 API
     * - 사용자 ID와 후원 아이템을 받아 포인트 후원 처리
     * - 후원 성공 시 성공 메시지 반환
     *
     * @param userId 후원자 사용자 ID
     * @param item 후원 아이템
     * @return 성공 메시지
     */
    @PostMapping("/point")
    @ResponseBody
    public ResponseEntity<String> donateWithPoint(
            @RequestParam("userId") Long userId,
            @RequestParam("item") Donation.DonationItem item
    ) {
        // 서비스에서 Donation(REQUESTED, POINT) 생성 및 포인트 차감 처리
        Donation donation = donationService.donateWithPoint(userId, item);

        // 후원 실패 시 에러 메시지 반환
        if (donation.getDonationStatus() == Donation.DonationStatus.FAILED) {
            return ResponseEntity.badRequest().body("포인트 후원 실패");
        }

        // 성공 메시지 반환
        return ResponseEntity.ok("포인트 후원 성공");
    }

    /**
     * 직접 결제 후원 API
     * - 사용자 ID와 후원 아이템을 받아 직접 결제 후원 처리
     * - 후원 준비 후 결제 페이지로 이동할 URL 반환
     *
     * @param userId 후원자 사용자 ID
     * @param item 후원 아이템
     * @return 결제 페이지로 이동할 URL
     */
    @PostMapping("/direct")
    @ResponseBody
    public ResponseEntity<String> donateWithDirectPayment(
            @RequestParam("userId") Long userId,
            @RequestParam("item") Donation.DonationItem item
    ) {
        // 서비스에서 Donation(REQUESTED, DIRECT) + Order(WAIT) 생성 후 order 화면으로 이동할 쿼리스트링 생성
        String redirectUrl = donationService.prepareDirectDonation(userId, item);

        return ResponseEntity.ok(redirectUrl);
    }

    /**
     * 후원 결제 승인 API
     * - PG사로부터 결제 승인 요청을 받아 처리
     * - 결제 처리 서비스 호출 후 성공 메시지 반환
     *
     * @param confirmRequest 결제 승인 요청 정보
     * @return 성공 메시지
     */
    @PostMapping("/confirm")
    public ResponseEntity<Object> confirmDonation(@RequestBody ConfirmRequest confirmRequest) {
        // 결제 처리 서비스 호출
        paymentProcessingService.createPayment(confirmRequest);

        // 성공 메시지 반환
        return ResponseEntity.ok("후원 결제 승인 성공");
    }

    /**
     * 내 후원 내역 조회 API
     * - 인증된 사용자의 후원 내역을 조회하여 반환
     *
     * @param user 인증된 사용자 정보
     * @return 후원 내역 리스트
     */
    @GetMapping("/my")
    public ResponseEntity<List<DonationResponse>> getMyDonations(@AuthenticationPrincipal User user) {
        // 후원 서비스 호출
        // user.getUsername()은 String 타입이므로 Long으로 변환 필요
        return ResponseEntity.ok(donationService.getMyDonations(Long.parseLong(user.getUsername())));
    }

}

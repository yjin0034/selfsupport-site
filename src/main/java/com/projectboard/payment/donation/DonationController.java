package com.projectboard.payment.donation;

import com.projectboard.payment.checkout.ConfirmRequest;
import com.projectboard.payment.wallet.WalletService;
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
    private final DonationService donationService; // 후원 서비스
    private final WalletService walletService;     // 지갑 서비스

    /**
     * 포인트로 후원
     * - 사용자 ID와 후원 아이템을 받아 포인트로 후원 처리
     * - 후원 성공 시 Donation 객체 반환
     * @param userId 후원자 사용자 ID
     * @param item 후원 아이템
     * @return 후원 기록 객체
     */
    @PostMapping("/api/point")
    @ResponseBody
    public Donation donateWithPoint(
            @RequestParam Long userId,
            @RequestParam String item
    ) {
        // 후원 서비스 호출
        return donationService.donateWithPoint(userId, Donation.DonationItem.valueOf(item));
    }

    /**
     * 직접 결제 후원
     * - 사용자 ID, 결제 승인 요청 정보, 후원 아이템을 받아 직접 결제 후원 처리
     * - 후원 성공 시 Donation 객체 반환
     * @param userId 후원자 사용자 ID
     * @param confirmRequest 결제 승인 요청 정보
     * @param item 후원 아이템
     * @return 후원 기록 객체
     */
    @PostMapping("/api/direct")
    @ResponseBody
    public Donation donateWithPayment(
            @RequestParam Long userId,
            @RequestBody ConfirmRequest confirmRequest,
            @RequestParam String item
    ) {
        // 후원 서비스 호출
        return donationService.donateWithPayment(userId, confirmRequest, Donation.DonationItem.valueOf(item));
    }

    /**
     * 내 후원 내역 조회
     * - 인증된 사용자의 후원 내역을 조회하여 반환
     * @param user 인증된 사용자 정보
     * @return 후원 내역 리스트
     */
    @GetMapping("/api/donations/my")
    public ResponseEntity<List<DonationResponse>> getMyDonations(@AuthenticationPrincipal User user) {
        // 후원 서비스 호출
        // user.getUsername()은 String 타입이므로 Long으로 변환 필요
        return ResponseEntity.ok(donationService.getMyDonations(Long.parseLong(user.getUsername())));
    }

}

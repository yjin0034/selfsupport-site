package com.projectboard.payment.donation;

import com.projectboard.payment.wallet.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * DonationViewController
 * - 후원 관련 뷰(화면) 렌더링을 담당하는 컨트롤러 클래스.
 * - 후원 페이지 뷰 렌더링 기능 포함.
 * - 타임리프(Thymeleaf) 템플릿과 연동하여 뷰를 반환.
 */
@Controller
@RequiredArgsConstructor
public class DonationViewController {
    // ===== 의존성 주입 =====
    private final WalletService walletService;          // 지갑 서비스

    /**
     * 후원 페이지 뷰 렌더링
     * - 인증된 사용자의 지갑 정보를 조회(또는 생성)하여 모델에 추가.
     * - 후원 아이템 목록을 모델에 추가.
     * - 후원 페이지 뷰 이름 반환.
     *
     * @param model 뷰 모델
     * @return 후원 페이지 뷰 이름
     */
    @GetMapping("/donations")
    public String donationPage(Model model) {
        // 인증된 사용자 ID
        Long userId = 1L;           // FIXME: 로그인 사용자 ID 사용 (테스트용 하드코딩)

        // 지갑 정보 가져오기 (없으면 생성)
        CreatedWalletResponse wallet;

        // 있으면 조회, 없으면 생성
        // 지갑이 없는 테스트 상황을 고려하여, 지갑이 없으면 지갑 생성토록 처리
        try {
            // 먼저 조회
            FindWalletResponse existing = walletService.findWalletByUserId(userId);
            // 있으면 기존 지갑 정보 사용
            wallet = new CreatedWalletResponse(existing.id(), existing.userId(), existing.balance());
        } catch (WalletNotFoundException e) {
            // 없으면 생성
            wallet = walletService.createWallet(new CreateWalletRequest(userId));
        }

        // 후원 아이템 목록 및 지갑 정보 모델에 추가
        model.addAttribute("items", Donation.DonationItem.values());    // 후원 아이템 목록
        model.addAttribute("balance", wallet.balance());                // 지갑 잔액
        model.addAttribute("userId", wallet.userId());                  // 사용자 ID

        // 후원 페이지 뷰 이름 반환
        return "payment/donation-page"; // templates/payment/donation-page.html
    }
}

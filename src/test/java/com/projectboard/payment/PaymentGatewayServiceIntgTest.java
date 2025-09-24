package com.projectboard.payment;

import com.projectboard.payment.checkout.ConfirmRequest;
import com.projectboard.payment.external.PaymentGatewayService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

/**
 * PaymentGatewayService 통합 테스트
 * - 실제 결제 게이트웨이 API 연동 확인용 (로컬에서만 수동 실행 권장)
 * - application-payment-test.yml 설정 사용
 * - 테스트 결과를 콘솔에 로깅
 */
@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("payment-test") // application-payment-test.yml 설정 사용
public class PaymentGatewayServiceIntgTest {
    // 테스트 결과 로거: 성공/실패/중단/비활성 상태 콘솔 출력
    @RegisterExtension
    static TestWatcher logWatcher = new TestWatcher() {
        @Override
        public void testSuccessful(ExtensionContext context) {
            System.out.println("✅ PASSED: " + context.getDisplayName());
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            System.err.println("❌ FAILED: " + context.getDisplayName());
            cause.printStackTrace(); // 빨간 스택트레이스 출력
        }

        @Override
        public void testAborted(ExtensionContext context, Throwable cause) {
            System.err.println("⚠️ ABORTED: " + context.getDisplayName());
        }

        @Override
        public void testDisabled(ExtensionContext context, Optional<String> reason) {
            System.out.println("⏸ DISABLED: " + context.getDisplayName() +
                    reason.map(r -> " — " + r).orElse(""));
        }
    };

    // ===== 의존성 주입 =====
    // SUT
    @Autowired PaymentGatewayService paymentGatewayService;    // 실제 결제 게이트웨이 서비스

    // ⚠️ 실제 PG 결제 승인 API 연동 확인용 테스트 (로컬에서만 수동 실행)
    // - orderId, paymentKey는 실제 결제 건마다 달라지므로 하드코딩 불가
    // - 따라서 CI/CD나 정기 테스트에서는 항상 실패할 수 있음
    // - @Disabled 처리하고, 특정 상황에서만 임시 실행할 예정
    @Test
    @Disabled("실제 PG 결제 승인 API 연동 확인용 - 로컬에서만 수동 실행")
    public void test() {
        // given - when - then 패턴 없이 실제 API 호출 테스트
        // 실제 결제 승인 API 호출
        paymentGatewayService.confirm(
                new ConfirmRequest(
                        "tgen_20250911175019QWb15",                 // 결제 고유 ID (실제 존재하는 결제 ID로 교체 필요)
                        "1bd23f7a-9009-41f1-a1da-638a85050d0b",               // 주문 번호 (임의 문자열)
                        "1000"                                                // 결제 금액 (문자열)
                )
        );

        // 성공 시 예외 없음, 실패 시 예외 발생

        // 디버깅 출력
        System.out.println("✅ PaymentGatewayServiceIntgTest completed successfully.");
    }
}
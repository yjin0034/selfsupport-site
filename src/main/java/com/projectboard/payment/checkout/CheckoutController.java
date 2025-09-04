package com.projectboard.payment.checkout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Controller
public class CheckoutController {
    /*
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
        - 결제 페이지의 사용자 경험 개선.
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

    /**
     * 결제 페이지 진입
     * - 사용자가 결제 버튼을 눌렀을 때 열리는 화면.
     * - templates/payment/checkout.html 뷰를 렌더링.
     */
    @GetMapping("/checkout")
    public String checkout() {
        return "payment/checkout";
    }

    /**
     * 결제 성공 페이지
     * - Toss 결제 성공 후 redirect 될 URL
     * - templates/payment/success.html 렌더링
     */
    @GetMapping("/success")
    public String success() {
        return "payment/success";
    }

    /**
     * 결제 성공 페이지
     * - Toss 결제 성공 후 redirect 될 URL
     * - templates/payment/success.html 렌더링
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
    @RequestMapping(value = "/confirm")
    public ResponseEntity<Object> confirmPayment(@RequestBody String jsonBody) throws Exception {
        // 1. 프론트엔드에서 전달받은 JSON(body)을 파싱
        final JsonNode jsonNode = new ObjectMapper().readTree(jsonBody);

        // 2. ConfirmRequest DTO로 변환 (결제승인 API에 필요한 필드만 추출)
        final ConfirmRequest request = new ConfirmRequest(
                jsonNode.get("paymentKey").asText(), // 결제 고유키
                jsonNode.get("orderId").asText(),    // 주문 ID
                jsonNode.get("amount").asText()      // 결제 금액
        );

        // 3. Toss Payments 인증 헤더 생성
        // - Toss API는 Basic Auth 방식 사용
        // - 사용자명: 시크릿 키, 비밀번호: 없음
        // - "시크릿키:" 문자열을 Base64 인코딩하여 Authorization 헤더에 포함
        String widgetSecretKey = "test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6";     // 테스트용 시크릿 키
        Base64.Encoder encoder = Base64.getEncoder();                          // Base64 인코더
        byte[] encodedBytes = encoder.encode((widgetSecretKey + ":").getBytes(StandardCharsets.UTF_8)); // "시크릿키:" 문자열을 UTF-8 바이트로 변환 후 Base64 인코딩
        String authorizations = "Basic " + new String(encodedBytes);           // "Basic " 접두어를 붙여 최종 인증 문자열 생성

        // 4. RestClient로 Toss Payments API 호출
        RestClient defaultClient = RestClient.create();

        // 5. POST 요청을 보내고, 응답을 Object 타입으로 매핑
        final Object object = defaultClient.post()                             // HTTP POST 요청
                .uri("https://api.tosspayments.com/v1/payments/confirm")   // 결제 승인 API URL
                .headers(httpHeaders -> {                           // 요청 헤더 설정
                    httpHeaders.set("Authorization", authorizations);          // 인증 헤더
                    httpHeaders.set("Content-Type", "application/json");       // 요청 바디 JSON 형식
                })
                .contentType(MediaType.APPLICATION_JSON)                       // 요청 Content-Type
                .body(request)                                                 // 요청 본문 (ConfirmRequest 직렬화됨)
                .retrieve()                                                    // 요청 전송
                .toEntity(Object.class);                                       // 응답을 Object 타입으로 매핑

        // 6. Toss에서 받은 응답을 클라이언트로 그대로 반환
        return ResponseEntity.ok(object);
    }

    /**
     * Confirm API 요청에 필요한 DTO
     * - paymentKey/orderId/amount 필드만 포함
     */
    public record ConfirmRequest(String paymentKey, String orderId, String amount
    ) {}

}

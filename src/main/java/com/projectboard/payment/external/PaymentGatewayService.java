package com.projectboard.payment.external;

import com.projectboard.payment.checkout.ConfirmRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 결제 게이트웨이 서비스
 * - 외부 결제 게이트웨이(Toss Payments)와의 통신을 담당하는 서비스 클래스.
 * - 결제 승인 요청을 처리하고, 응답을 매핑하는 기능 제공.
 */
@Service
public class PaymentGatewayService {
    // ===== 상수 정의 =====
    private static final Base64.Encoder encoder = Base64.getEncoder();                    // Base64 인코더
    private static final String SECRET = "test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6";        // 테스트용 시크릿 키

    // ===== 의존성 주입 =====
    @Value("${pg.url}")  // application.yml 에 정의된 값 주입
    public String pgUrl; // Toss Payments 결제 승인 API URL

    /**
     * 결제 승인 요청
     * - ConfirmRequest 객체를 받아 Toss Payments API에 승인 요청을 보냄
     * - 응답 상태 코드가 4xx/5xx인 경우 예외 처리
     * @param confirmRequest 결제 승인 요청 정보
     * @throws IllegalArgumentException 결제 요청이 실패한 경우
     */
    public void confirm(ConfirmRequest confirmRequest) {
        // ===== Toss Payments API 호출 =====
        // 1. Toss Payments 인증 헤더 생성
        // - Toss API는 Basic Auth 방식 사용
        // - 사용자명: 시크릿 키, 비밀번호: 없음
        // - "시크릿키:" 문자열을 Base64 인코딩하여 Authorization 헤더에 포함
        byte[] encodedBytes = encoder.encode((SECRET + ":").getBytes(StandardCharsets.UTF_8)); // "시크릿키:" 문자열을 UTF-8 바이트로 변환 후 Base64 인코딩
        String authorizations = "Basic " + new String(encodedBytes);                           // "Basic " 접두어를 붙여 최종 인증 문자열 생성

        // 2. RestClient로 Toss Payments API 호출 준비
        RestClient defaultClient = RestClient.create();

        // 3. POST 요청을 보내고, 응답을 Object 타입으로 매핑
        final ResponseEntity<Object> object = defaultClient.post()             // HTTP POST 요청
                .uri(pgUrl)                                                    // 결제 승인 API URL
                .headers(httpHeaders -> {                           // 요청 헤더 설정
                    httpHeaders.set("Authorization", authorizations);          // 인증 헤더
                    httpHeaders.set("Content-Type", "application/json");       // 요청 바디 JSON 형식
                })
                .contentType(MediaType.APPLICATION_JSON)                       // 요청 Content-Type
                .body(confirmRequest)                                          // 요청 본문 (ConfirmRequest 직렬화됨)
                .retrieve()                                                    // 요청 전송
                .toEntity(Object.class);                                       // 응답을 Object 타입으로 매핑

        // 4. 응답 상태 코드가 4xx/5xx인 경우 예외 처리
        // 타임아웃 같은 케이스는 바로 Exception 이 throw 됨
        if (object.getStatusCode().isError()) {
            throw new IllegalArgumentException("결제 요청이 실패했습니다.");
        }
    }

    /**
     * Toss Payments API 응답 매핑용 record 클래스
     * - 결제 승인 응답 데이터를 매핑하기 위한 간단한 데이터 구조체.
     * @param paymentKey 결제 키
     * @param orderId 주문 ID
     * @param orderName 주문 이름
     * @param status 결제 상태
     * @param amount 결제 금액
     * @return Response 객체
     */
    public record Response(String paymentKey, String orderId, String orderName, String status, String amount) {
    }

}
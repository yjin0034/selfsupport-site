package com.projectboard.payment.retry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectboard.payment.checkout.ConfirmRequest;
import com.projectboard.payment.processing.PaymentProcessingService;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;

/**
 * 재시도 요청 서비스
 * - 외부 결제 게이트웨이와의 통신 실패 시 재시도 요청을 처리하는 서비스 클래스.
 * - 재시도 요청을 조회하고, 재시도 작업을 수행하며, 상태를 업데이트.
 */
@Slf4j
@AllArgsConstructor
@Service
public class RetryRequestService {
    // ==== 의존성 주입 ===
    private final RetryRequestRepository retryRequestRepository;     // 재시도 요청 리포지토리
    private final PaymentProcessingService paymentProcessingService; // 결제 처리 서비스
    private final ObjectMapper objectMapper;                         // JSON 객체 매퍼

    // 재시도 요청 처리 메서드
    @SneakyThrows                                                    // checked 예외를 자동으로 처리
    public void retry(Long retryRequestId) {
        // 재시도 요청 조회
        // - ID로 재시도 요청을 조회하고, 없으면 예외 발생
        final RetryRequest request = retryRequestRepository
                .findById(retryRequestId)
                .orElseThrow();

        // 이미 완료된 요청인지 확인
        // - 상태가 FAILURE 또는 SUCCESS인 경우 이미 완료된 요청이므로 로그 기록 후 종료
        if (request.getStatus() == RetryRequest.Status.FAILURE || request.getStatus() == RetryRequest.Status.SUCCESS) {
            // 완료된 요청이면 로그 기록 후 종료
            log.info("RetryRequest id={}는 이미 완료된 상태입니다. status={}", retryRequestId, request.getStatus());
            return;
        }

        // 요청 JSON을 ConfirmRequest 객체로 변환
        final ConfirmRequest confirmRequest = objectMapper.readValue(
                request.getRequestJson(), ConfirmRequest.class); // JSON -> 객체 변환

        // 재시도 작업 수행
        try {
            // 결제 처리 서비스의 createCharge 메서드 호출
            // - 두 번째 인자 true는 재시도 작업임을 나타냄
            paymentProcessingService.createCharge(confirmRequest, true);

            // 재시도 작업이 성공하면 상태를 SUCCESS로 변경
            request.setStatus(RetryRequest.Status.SUCCESS);
        }
        // 재시도 작업 중 예외 발생 시 처리
        // - SocketTimeoutException 또는 RestClientException인 경우 재시도 가능
        // - 그 외 예외는 재시도 불가능하므로 FAILURE로 상태 변경
        catch (Exception e) {
            // 예외 로그 기록
            log.error("RetryRequest id={} 처리 중 오류 발생: {}", retryRequestId, e.getMessage(), e);

            // 현재 재시도 횟수
            int currentRetryCount = request.getRetryCount();

            // 재시도 가능 오류: retryCount 를 올려준다.
            // SocketTimeoutException 또는 RestClientException 인 경우 재시도 가능
            if (e instanceof SocketTimeoutException || e instanceof RestClientException) {
                request.setStatus(RetryRequest.Status.IN_PROGRESS); // 상태를 IN_PROGRESS로 설정
                request.setRetryCount(currentRetryCount + 1);       // 재시도 횟수 증가
            }
            // 재시도 불가능한 오류: FAILURE 로 상태 변경
            else {
                request.setStatus(RetryRequest.Status.FAILURE);
            }
        }
        // 마지막으로 요청 상태와 수정 시각 업데이트 후 저장
        finally {
            request.setUpdatedAt(LocalDateTime.now()); // 수정 시각 업데이트
            retryRequestRepository.save(request);      // 변경된 요청 저장
        }

    }
}

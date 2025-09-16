package com.projectboard.payment.retry;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 재시도 요청 컨트롤러
 * - 외부 결제 게이트웨이와의 통신 실패 시 재시도 요청을 처리하는 REST 컨트롤러.
 * - 특정 재시도 요청 ID로 재시도 작업을 트리거하는 엔드포인트를 제공.
 */
@Slf4j
@RestController
@AllArgsConstructor
public class RetryRequestController {
    private final RetryRequestService retryRequestService;

    /**
     * 재시도 요청 처리 엔드포인트
     * - 지정된 재시도 요청 ID에 대해 재시도 작업을 수행.
     * - 재시도 요청이 이미 완료된 경우 아무 작업도 수행하지 않음.
     *
     * @param retryId 재시도 요청 ID
     */
    @PostMapping("/api/retry-request/{retryId}")
    public void retry(@PathVariable("retryId") Long retryId) {
        // 로그 기록
        log.info("재시도 요청이 수신되었습니다. id={}", retryId);
        // 재시도 서비스 호출
        retryRequestService.retry(retryId);
    }

}

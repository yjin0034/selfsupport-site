package com.projectboard.payment.retry;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 재시도 요청 엔티티
 * - 외부 결제 게이트웨이와의 통신 실패 시 재시도 요청을 저장하는 엔티티 클래스.
 * - 요청 데이터, 재시도 횟수, 상태 등을 포함.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
public class RetryRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 기본 키, 자동 생성
    private Long id;                 // 재시도 요청 ID

    private String requestJson;      // 요청 데이터 JSON

    private String requestId;        // 고유 요청 ID

    private Integer retryCount;      // 재시도 횟수

    private String errorResponse;    // 오류 응답 메시지

    @Enumerated(EnumType.STRING)
    private Status status;           // 재시도 요청 상태

    @Enumerated(EnumType.STRING)
    private Type type;               // 재시도 요청 유형

    private LocalDateTime createdAt; // 생성 시각
    private LocalDateTime updatedAt; // 수정 시각

    /*
     * 생성자
     * - 재시도 요청 생성 시 기본값 설정
     */
    public RetryRequest(String requestJson, String requestId, String errorResponse, Type type) {
        this.requestJson = requestJson;
        this.requestId = requestId;
        this.retryCount = 0;                    // 초기 재시도 횟수: 0
        this.errorResponse = errorResponse;     // 오류 응답 메시지
        this.status = Status.IN_PROGRESS;       // 기본 상태: 진행 중
        this.type = type;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 재시도 요청 상태
    public enum Status {
        IN_PROGRESS, SUCCESS, FAILURE // 진행 중, 성공, 실패
    }

    // 재시도 요청 유형
    public enum Type {
        CONFIRM // 결제 승인 요청
    }

}

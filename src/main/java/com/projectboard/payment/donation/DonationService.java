package com.projectboard.payment.donation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectboard.payment.checkout.ConfirmRequest;
import com.projectboard.payment.external.PaymentGatewayService;
import com.projectboard.payment.transaction.Transaction;
import com.projectboard.payment.transaction.TransactionRepository;
import com.projectboard.payment.transaction.TransactionService;
import com.projectboard.payment.wallet.Wallet;
import com.projectboard.payment.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DonationService
 * - 후원 관련 비즈니스 로직을 처리하는 서비스 클래스.
 * - 포인트 후원 및 직접 결제 후원 기능 제공.
 * - 트랜잭션 관리 및 예외 처리 포함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DonationService {
    // ===== 의존성 주입 =====
    private final DonationRepository donationRepository;            // 후원 리포지토리
    private final WalletRepository walletRepository;                // 지갑 리포지토리
    private final PaymentGatewayService paymentGatewayService;      // 외부 결제 게이트웨이 서비스
    private final TransactionService transactionService;            // 거래 서비스
    private final TransactionRepository transactionRepository;      // 거래 리포지토리

    private final ObjectMapper objectMapper;                        // JSON 변환기

    /**
     * 포인트로 후원
     * - 사용자 ID와 후원 아이템을 받아 포인트로 후원 처리
     * - 후원 성공 시 Donation 객체 반환
     * - 잔액 부족 시 후원 실패 기록 저장 및 예외 발생
     * - 지갑 조회, 잔액 확인, 잔액 차감, 거래 기록 생성, 후원 기록 생성 및 저장 순으로 처리
     * - 트랜잭션 처리 보장 (모든 작업이 성공해야 커밋, 하나라도 실패하면 롤백)
     * - 후원 아이템에 따른 가격 책정
     *
     * @param userId 후원자 사용자 ID
     * @param item 후원 아이템
     * @return 후원 기록 객체
     */
    @Transactional
    public Donation donateWithPoint(Long userId, Donation.DonationItem item) {
        // 1. 지갑 조회
        Wallet wallet = walletRepository.findWalletByUserId(userId)
                .orElseThrow((() -> new IllegalArgumentException("지갑이 존재하지 않습니다. userId=" + userId)));

        // 잔액 확인
        // 후원 아이템에 따른 가격
        BigDecimal price = item.getPrice();

        // 2. 잔액 부족 처리
        // 잔액 부족 시, 후원 실패 기록 저장 후 예외 던지기
        if (wallet.getBalance().compareTo(price) < 0) {

            // 실패한 후원 기록 생성
            Donation failedDonation = Donation.builder()
                    .userId(userId)                                     // 후원자 ID
                    .donationItem(item)                                 // 후원 아이템
                    .amount(price)                                      // 후원 금액
                    .donationType(Donation.DonationType.POINT)          // 포인트 후원
                    .donationStatus(Donation.DonationStatus.FAILED)     // 실패 처리
                    .createdAt(java.time.LocalDateTime.now())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();                                           // 빌더 패턴으로 후원 엔티티 생성

            // 실패한 후원 기록 저장
            donationRepository.save(failedDonation);

            // 예외 던지기
            throw new IllegalArgumentException("잔액이 부족합니다. balance=" + wallet.getBalance() + ", price=" + price);
        }

        // 3. 잔액 충분 시, 후원 처리
        // 잔액 차감 및 지갑 저장
        wallet.setBalance(wallet.getBalance().subtract(price));     // balance - price
        walletRepository.save(wallet);

        // 트랜잭션 생성 및 저장
        Transaction tx = transactionService.createPointDonationTransaction(
                userId,
                wallet.getId(),
                price
        );

        // 후원 기록 생성
        // 후원 엔티티 생성
        Donation donation = Donation.builder()
                .userId(userId)                                     // 후원자 ID
                .donationItem(item)                                 // 후원 아이템
                .amount(price)                                      // 후원 금액
                .donationType(Donation.DonationType.POINT)          // 포인트 후원
                .donationStatus(Donation.DonationStatus.COMPLETED)  // 즉시 완료 처리
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();                                           // 빌더 패턴으로 후원 엔티티 생성

        // 후원 기록 저장
        return donationRepository.save(donation);
    }

    /**
     * 직접 결제로 후원
     * - 사용자 ID, 결제 확인 요청, 후원 아이템을 받아 직접 결제 후원 처리
     * - PG 승인 요청 및 거래 기록 생성
     * - 후원 성공 시 Donation 객체 반환
     * - PG 승인 실패 시 후원 실패 기록 저장 및 예외 발생
     * - 중복 결제 방지 (이미 처리된 orderId인지 확인)
     * - 트랜잭션 처리 보장 (모든 작업이 성공해야 커밋, 하나라도 실패하면 롤백)
     *
     * @param userId 후원자 사용자 ID
     * @param confirmRequest 결제 확인 요청 정보
     * @param item 후원 아이템
     * @return 후원 기록 객체
     */
    @Transactional
    public Donation donateWithPayment(Long userId, ConfirmRequest confirmRequest, Donation.DonationItem item) {
        // 1. 중복 결제 방지
        // 이미 처리된 orderId인지 확인
        if (transactionRepository.existsByOrderId(confirmRequest.orderId())) {
            throw new IllegalArgumentException("이미 처리된 후원 요청입니다. orderId=" + confirmRequest.orderId());
        }

        // PG 승인 요청 및 거래 기록 생성
        try {
            // PG 승인 요청
            paymentGatewayService.confirm(confirmRequest);

            // 후원 기록 생성 (초기 상태는 REQUESTED)
            Donation donation = Donation.builder()
                    .userId(userId)                                     // 후원자 ID
                    .donationItem(item)                                 // 후원 아이템
                    .amount(item.getPrice())                            // 후원 금액
                    .donationType(Donation.DonationType.DIRECT)         // 직접 결제 후원
                    .donationStatus(Donation.DonationStatus.REQUESTED)  // 요청됨 상태
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();                                           // 빌더 패턴으로 후원 엔티티 생성

            // 트랜잭션 생성 및 저장
            Transaction tx = transactionService.pgPayment(
                    userId,
                    confirmRequest.orderId(),
                    item.getPrice(),
                    confirmRequest.paymentKey()
            );

            // 후원 기록 업데이트
            // 성공 처리 및 거래 정보 저장
            donation.setDonationStatus(Donation.DonationStatus.COMPLETED);  // 성공 처리
            donation.setTransaction(tx);                                    // 거래 정보 저장

            // 후원 기록 저장
            donationRepository.save(donation);

            // 결과 반환
            return donation;
        }
        // 예외 처리
        catch (RestClientException e) {
            // PG 승인 실패 시, 후원 기록을 FAILED로 업데이트
            log.error("PG 승인 실패: orderId={}, message={}", confirmRequest.orderId(), e.getMessage());

            // 실패한 후원 기록 생성
            Donation failedDonation = Donation.builder()
                    .userId(userId)                                     // 후원자 ID
                    .donationItem(item)                                 // 후원 아이템
                    .amount(item.getPrice())                            // 후원 금액
                    .donationType(Donation.DonationType.DIRECT)         // 직접 결제 후원
                    .donationStatus(Donation.DonationStatus.FAILED)     // 실패 처리
                    .errorMessage(e.getMessage())                       // 에러 메시지 저장
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();                                           // 빌더 패턴으로 후원 엔티티 생성


            // 후원 기록 업데이트
            failedDonation.setDonationStatus(Donation.DonationStatus.FAILED);     // 실패 처리
            failedDonation.setErrorMessage(e.getMessage());                       // 에러 메시지 저장

            // 실패한 후원 기록 저장
            donationRepository.save(failedDonation);

            // 예외 다시 던지기
            throw e;
        }
    }

    /**
     * 나의 후원 내역 조회
     * - 사용자 ID를 받아 해당 사용자의 모든 후원 내역을 조회하여 반환
     * - 후원 내역은 생성 시각 내림차순으로 정렬
     *
     * @param userId 사용자 ID
     * @return 후원 내역 리스트
     */
    public List<DonationResponse> getMyDonations(Long userId) {
        // 후원 내역 조회 및 DonationResponse로 변환
        return donationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()                               // 스트림 생성
                .map(DonationResponse::from)            // DonationResponse로 변환
                .collect(Collectors.toList());          // 리스트로 수집 및 반환
    }

}

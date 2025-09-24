package com.projectboard.payment.transaction;

import com.projectboard.payment.wallet.AddBalanceWalletRequest;
import com.projectboard.payment.wallet.AddBalanceWalletResponse;
import com.projectboard.payment.wallet.FindWalletResponse;
import com.projectboard.payment.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * TransactionService
 * - 트랜잭션(거래) 관련 비즈니스 로직을 처리하는 서비스 클래스.
 * - 충전 및 결제 트랜잭션 처리, PG 결제 트랜잭션 생성, 포인트 후원 트랜잭션 생성 기능 포함.
 * - 멱등성 보장, 입력 유효성 검사, 예외 처리 포함.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {
    // ===== 의존성 주입 =====
    private final WalletService walletService;                  // 지갑 서비스
    private final TransactionRepository transactionRepository;  // 트랜잭션 리포지토리

    /**
     * 충전 트랜잭션 처리
     * - 요청된 금액만큼 지갑에 잔액을 추가하고, 충전 트랜잭션을 생성.
     * - 멱등성 보장을 위해 orderId에 고유 제약 조건을 설정.
     * - 이미 존재하는 orderId에 대한 요청이 들어올 경우, 기존 트랜잭션을 조회하여 반환.
     * - 입력 유효성 검사 포함.
     * - PG 결제 트랜잭션 지원.
     * - 충전 요청이 성공적으로 처리되면 충전 결과 정보를 반환.
     * - 충전 요청이 실패할 경우 적절한 예외를 발생시킴.
     *
     * @param request 충전 요청 정보
     * @return 충전 결과 정보
     */
    public ChargeTransactionResponse charge(ChargeTransactionRequest request) {
        // 입력 유효성: 결제 금액은 반드시 양수여야 한다
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new IllegalArgumentException("결제 금액은 양수여야 합니다.");
        }

        // 멱등성 보장: orderId에 고유 제약 조건(Unique Constraint) 설정
        // 1. orderId가 없으면 잔액 충전 및 트랜잭션 생성
        try {
            // 사용자 지갑 조회
            final FindWalletResponse findWalletResponse = walletService
                    .findWalletByWalletId(request.walletId());
            // 지갑이 존재하지 않는 경우 예외 처리
            if (findWalletResponse == null) {
                throw new RuntimeException("사용자 지갑이 존재하지 않습니다.");
            }

            // 지갑 잔액 업데이트
            final AddBalanceWalletResponse wallet = walletService.addBalance(
                    new AddBalanceWalletRequest(findWalletResponse.id(), request.amount()));

            // 충전 트랜잭션 생성
            final Transaction transaction = Transaction.createChargeTransaction(
                    findWalletResponse.userId(),            // 사용자 ID
                    findWalletResponse.id(),                // 지갑 ID
                    request.orderId(),
                    request.amount()
            );
            // 트랜잭션 저장
            transactionRepository.save(transaction);
            // 결과 응답 반환
            return new ChargeTransactionResponse(wallet.walletId(), wallet.balance());
        }
        // 2. orderId에 대한 고유 제약 조건 위반 시, 이미 존재하는 트랜잭션을 조회하여 반환
        // 중복 예외 발생 (이미 다른 트랜잭션에서 insert 성공)
        // → 재조회해서 결과 반환 (멱등성 보장)
        catch (DataIntegrityViolationException e) {
            // 기존 트랜잭션 재조회
            Transaction existing = transactionRepository.findTransactionByOrderId(request.orderId())
                    .orElseThrow(() -> new RuntimeException("멱등 보장 실패: 기존 트랜잭션 없음"));
            // 결과 응답 반환
            return new ChargeTransactionResponse(existing.getWalletId(), existing.getAmount());
        }
    }

    /**
     * 결제 트랜잭션 처리
     * - 요청된 금액만큼 지갑에서 잔액을 차감하고, 결제 트랜잭션을 생성.
     * - 멱등성 보장을 위해 donationId에 고유 제약 조건을 설정.
     * - 이미 존재하는 donationId에 대한 요청이 들어올 경우, 기존 트랜잭션을 조회하여 반환.
     * - 입력 유효성 검사 포함.
     * - PG 결제 트랜잭션 지원.
     * - 결제 요청이 성공적으로 처리되면 결제 결과 정보를 반환.
     * - 결제 요청이 실패할 경우 적절한 예외를 발생시킴.
     *
     * @param request 결제 요청 정보
     * @return 결제 결과 정보
     */
    public PaymentTransactionResponse payment(PaymentTransactionRequest request) {
        // 입력 유효성: 결제 금액은 반드시 양수여야 한다
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new IllegalArgumentException("결제 금액은 양수여야 합니다.");
        }

        // 멱등성 보장: donationId에 고유 제약 조건(Unique Constraint) 설정
        // 1. donationId가 없으면 잔액 차감 및 트랜잭션 생성
        try {
            // 사용자 지갑 조회
            final FindWalletResponse findWalletResponse = walletService
                    .findWalletByWalletId(request.walletId());

            // 지갑 잔액 차감
            final AddBalanceWalletResponse wallet = walletService.addBalance(
                    new AddBalanceWalletRequest(
                            findWalletResponse.id(),
                            request.amount().negate()  // 금액을 음수로 변환하여 차감 처리
                    )
            );

            // 결제 트랜잭션 생성
            final Transaction transaction = Transaction.createPaymentTransaction(
                    wallet.userId(),
                    wallet.walletId(),
                    request.donationId(),
                    request.amount()
            );
            // 트랜잭션 저장
            transactionRepository.save(transaction);
            // 결과 응답 반환
            return new PaymentTransactionResponse(wallet.walletId(), wallet.balance());
        }
        // 2. donationId에 대한 고유 제약 조건 위반 시, 이미 존재하는 트랜잭션을 조회하여 반환
        // 중복 예외 발생 (이미 다른 트랜잭션에서 insert 성공)
        // → 재조회해서 결과 반환 (멱등성 보장)
        catch (DataIntegrityViolationException e) {
            // 기존 트랜잭션 재조회
            Transaction existing = transactionRepository.findTransactionByOrderId(request.donationId())
                    .orElseThrow(() -> new RuntimeException("멱등 보장 실패: 기존 트랜잭션 없음"));
            // 결과 응답 반환
            return new PaymentTransactionResponse(existing.getWalletId(), existing.getAmount());
        }
    }

    /**
     * PG 결제 트랜잭션 생성 (외부 결제 게이트웨이용)
     * - 사용자 ID, 주문 ID, 결제 금액, 결제 키를 받아 PG 결제 트랜잭션을 생성하고 저장.
     * - 생성된 트랜잭션 객체를 반환.
     *
     * @param userId 사용자 ID
     * @param orderId 주문 ID
     * @param amount 결제 금액
     * @param paymentKey 결제 키
     * @return 생성된 트랜잭션 객체
     */
    public Transaction pgPayment(Long userId, String orderId, BigDecimal amount, String paymentKey) {
        // PG 결제 트랜잭션 생성
        Transaction tx = Transaction.createPgPaymentTransaction(
                userId, orderId, amount, paymentKey
        );

        // 트랜잭션 저장
        return transactionRepository.save(tx); // 저장된 트랜잭션 반환
    }

    /**
     * 포인트 후원 트랜잭션 생성 (내부 포인트 결제용)
     * - 사용자 ID, 지갑 ID, 후원 금액을 받아 포인트 후원 결제 트랜잭션을 생성하고 저장.
     * - 생성된 트랜잭션 객체를 반환.
     *
     * @param userId 사용자 ID
     * @param walletId 지갑 ID
     * @param amount 후원 금액
     * @return 생성된 트랜잭션 객체
     */
    public Transaction createPointDonationTransaction(Long userId, Long walletId, BigDecimal amount) {
        // 포인트 후원 결제 트랜잭션 생성
        Transaction tx = Transaction.createPointDonationTransaction(userId, walletId, amount);

        // 트랜잭션 저장
        return transactionRepository.save(tx);
    }

}

package com.projectboard.payment.transaction;

import com.projectboard.payment.wallet.AddBalanceWalletRequest;
import com.projectboard.payment.wallet.AddBalanceWalletResponse;
import com.projectboard.payment.wallet.FindWalletResponse;
import com.projectboard.payment.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private final WalletService walletService;
    private final TransactionRepository transactionRepository;

    // ===== 충전 트랜잭션 처리 =====
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
                    request.walletId(), wallet.id(),
                    request.orderId(), request.amount()
            );
            // 트랜잭션 저장
            transactionRepository.save(transaction);
            // 결과 응답 반환
            return new ChargeTransactionResponse(wallet.id(), wallet.balance());
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

    // ===== 결제 트랜잭션 처리 =====
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
                    wallet.userId(), wallet.id(),
                    request.donationId(), request.amount()
            );
            // 트랜잭션 저장
            transactionRepository.save(transaction);
            // 결과 응답 반환
            return new PaymentTransactionResponse(wallet.id(), wallet.balance());
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
}

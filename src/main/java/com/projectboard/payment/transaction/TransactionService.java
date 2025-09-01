package com.projectboard.payment.transaction;

import com.projectboard.payment.wallet.AddBalanceWalletRequest;
import com.projectboard.payment.wallet.AddBalanceWalletResponse;
import com.projectboard.payment.wallet.FindWalletResponse;
import com.projectboard.payment.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private final WalletService walletService;
    private final TransactionRepository transactionRepository;

    // 충전 트랜잭션 처리
    public ChargeTransactionResponse charge(ChargeTransactionRequest request) {
        // 중복된 거래 체크
        if (transactionRepository.findTransactionByOrderId(request.orderId()).isPresent()) {
            throw new RuntimeException("이미 충전된 거래입니다.");
        };

        // 사용자 지갑 조회
        final FindWalletResponse findWalletResponse = walletService
                .findWalletByWalletId(request.userId());
        // 지갑이 존재하지 않는 경우 예외 처리
        if (findWalletResponse == null) {
            throw new RuntimeException("사용자 지갑이 존재하지 않습니다.");
        }

        // 지갑 잔액 추가
        final AddBalanceWalletResponse wallet = walletService.addBalance(
                new AddBalanceWalletRequest(findWalletResponse.id(), request.amount()));
        // 충전 트랜잭션 생성
        final Transaction transaction = Transaction.createChargeTransaction(
                request.userId(), wallet.id(),
                request.orderId(), request.amount()
        );
        // 트랜잭션 저장
        transactionRepository.save(transaction);
        // 결과 응답 반환
        return new ChargeTransactionResponse(wallet.id(), wallet.balance());
    }

    // 결제 트랜잭션 처리
    public PaymentTransactionResponse payment(PaymentTransactionRequest request) {
        // 중복된 결제 거래 체크
        if (transactionRepository.findTransactionByOrderId(request.donationId()).isPresent()) {
            throw new RuntimeException("이미 결제된 후원입니다.");
        };

        // 입력 유효성: 결제 금액은 반드시 양수여야 한다
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new IllegalArgumentException("결제 금액은 양수여야 합니다.");
        }

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
}

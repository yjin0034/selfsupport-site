package com.projectboard.payment.wallet;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Service
public class WalletService {
    private final BigDecimal BALANCE_LIMIT = new BigDecimal(1_000_000);

    private final WalletRepository walletRepository;

    // 지갑 생성
    @Transactional
    public CreatedWalletResponse createWallet(CreateWalletRequest request) {
        // 지갑 중복 체크
        walletRepository.findWalletByUserId(request.userId())
                .ifPresent(existing -> {
                    throw new RuntimeException("해당 사용자의 지갑이 이미 존재합니다.");
                });

        // 새로운 지갑 생성
        final Wallet wallet = walletRepository.save(new Wallet(request.userId()));
        // 결과 응답 반환
        return new CreatedWalletResponse(
                wallet.getId(), wallet.getUserId(), wallet.getBalance());
    }

    // 지갑 ID로 지갑 조회
    public FindWalletResponse findWalletByWalletId(Long walletId) {
        return walletRepository.findById(walletId)   // 지갑 조회
                .map(wallet -> new FindWalletResponse( // 결과 매핑
                        wallet.getId(), wallet.getUserId(), wallet.getBalance(),
                        wallet.getCreatedAt(), wallet.getUpdatedAt()
                ))
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    // 잔액 충전
    @Transactional
    public AddBalanceWalletResponse addBalance(AddBalanceWalletRequest request) {
        /*
        1. 잔액이 마이너스가 되면 오류가 발생해야 한다.
        2. 최대 충전한도는 100만원이다.
         */

        // 엔티티 조회 (존재 검사)
        final Wallet wallet = walletRepository.findById(request.walletId())
                .orElseThrow(() -> new WalletNotFoundException(request.walletId()));

        // 변경 금액 검증
        BigDecimal amt = request.amount();
        // null 또는 0원인 경우 예외
        if (amt == null || amt.signum() == 0) {
            throw new IllegalArgumentException("변경 금액은 0일 수 없습니다.");
        }

        // 충전 금액 검증
        // 도메인에 위임 (검증, 계산, 상태 변경)
        // 부호에 따라 적절한 도메인 동작 호출
        if (amt.signum() > 0) {
            // 충전 분기
            wallet.charge(amt, BALANCE_LIMIT);
        } else {
            // 차감 분기 : 내부 도메인 연산은 '양수'로 받도록 통일
            wallet.spend(amt.abs());
        }

        // 상태 변경
        walletRepository.save(wallet);

        // 결과 응답 반환
        return new AddBalanceWalletResponse(
                wallet.getId(), wallet.getUserId(), wallet.getBalance(),
                wallet.getCreatedAt(), wallet.getUpdatedAt()
        );
    }

}

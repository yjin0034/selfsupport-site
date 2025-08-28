package com.projectboard.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
public class WalletService {
    private final BigDecimal BALANCE_LIMIT = new BigDecimal(1_000_000);

    private final WalletRepository walletRepository;

    @Transactional
    public CreatedWalletResponse createWallet(CreateWalletRequest request) {
        // 중복 체크
        walletRepository.findWalletByUserId(request.userId())
                .ifPresent(existing -> {
                    throw new RuntimeException("해당 사용자의 지갑이 이미 존재합니다.");
                });

        // 신규 생성
        final Wallet wallet = walletRepository.save(new Wallet(request.userId()));
        return new CreatedWalletResponse(
                wallet.getId(), wallet.getUserId(), wallet.getBalance());
    }

    public FindWalletResponse findWalletByUserId(Long userId) {
        return walletRepository.findWalletByUserId(userId)
                .map(wallet -> new FindWalletResponse(
                        wallet.getId(), wallet.getUserId(), wallet.getBalance(),
                        wallet.getCreatedAt(), wallet.getUpdatedAt()
                ))
                .orElse(null);
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
                .orElseThrow(() -> new RuntimeException("해당 사용자의 지갑이 존재하지 않습니다."));

        // 충전 금액 검증
        // 도메인에 위임 (검증, 계산, 상태 변경)
        wallet.charge(request.amount(), BALANCE_LIMIT);

        // 상태 변경
        walletRepository.save(wallet);

        // 응답 생성
        return new AddBalanceWalletResponse(
                wallet.getId(), wallet.getUserId(), wallet.getBalance(),
                wallet.getCreatedAt(), wallet.getUpdatedAt()
        );
    }

}

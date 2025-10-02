package com.projectboard.payment.wallet;

import lombok.RequiredArgsConstructor;
import org.hibernate.dialect.lock.OptimisticEntityLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 지갑 서비스 클래스
 * - 지갑 생성, 조회, 잔액 추가(충전/차감) 기능 제공
 * - 충전 한도(BALANCE_LIMIT) 설정
 * - 낙관적 락(Optimistic Lock) 재시도 로직 포함
 */
@RequiredArgsConstructor
@Service
public class WalletService {
    // ===== 상수 =====
    // 충전 한도
    // 잔액 1,000,000원 초과 불가
    private final BigDecimal BALANCE_LIMIT = new BigDecimal(1_000_000);

    // ===== 의존성 주입 =====
    private final WalletRepository walletRepository;            // 지갑 리포지토리

    /**
     * 지갑 생성
     * - userId에 고유 제약 조건(Unique Constraint) 설정으로 멱등성 보장
     * - 이미 존재하는 userId로 생성 시, 기존 지갑 반환
     *
     * @param request 지갑 생성 요청 정보
     * @return 생성된 지갑 정보
     */
    @Transactional
    public CreatedWalletResponse createWallet(CreateWalletRequest request) {
        // 멱등성 보장: userId에 고유 제약 조건(Unique Constraint) 설정
        // 1. 먼저 DB에 insert 시도
        // userId가 없으면 새로 생성
        try {
            // 새로운 지갑 생성
            Wallet wallet = walletRepository.save(new Wallet(request.userId()));

            // 결과 응답 반환
            return new CreatedWalletResponse(wallet.getId(), wallet.getUserId(), wallet.getBalance());
        }
        // 2. userId에 대한 고유 제약 조건 위반 시, 이미 존재하는 지갑을 조회하여 반환
        // 중복 예외 발생 (이미 다른 트랜잭션에서 insert 성공)
        // → 재조회해서 결과 반환 (멱등성 보장)
        catch (DataIntegrityViolationException e) {
            // 기존 지갑 재조회
            // 존재하지 않으면 예외 발생
            Wallet existing = walletRepository.findWalletByUserId(request.userId())
                    .orElseThrow(() -> new RuntimeException("이미 존재하는 지갑을 찾을 수 없습니다."));

            // 결과 응답 반환
            return new CreatedWalletResponse(existing.getId(), existing.getUserId(), existing.getBalance());
        }
    }

    /** 지갑 ID로 지갑 조회
     * - 지갑 ID로 지갑을 조회하고, 없으면 예외 발생
     *
     * @param walletId 지갑 ID
     * @return 조회된 지갑 정보
     */
    @Transactional(readOnly = true)
    public FindWalletResponse findWalletByWalletId(Long walletId) {
        // 지갑 조회
        // 존재하지 않으면 예외 발생
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        // 결과 응답 반환
        return new FindWalletResponse(
                wallet.getId(),
                wallet.getUserId(),
                wallet.getBalance(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }

    /** 사용자 ID로 지갑 조회
     * - 사용자 ID로 지갑을 조회하고, 없으면 예외 발생
     *
     * @param userId 사용자 ID
     * @return 조회된 지갑 정보
     */
    @Transactional(readOnly = true)
    public FindWalletResponse findWalletByUserId(Long userId) {
        // 지갑 조회
        // 존재하지 않으면 예외 발생
        Wallet wallet = walletRepository.findWalletByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));

        // 결과 응답 반환
        return new FindWalletResponse(
                wallet.getId(),
                wallet.getUserId(),
                wallet.getBalance(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt());
    }

    /** 잔액 추가 (충전/차감)
     * - 낙관적 락(Optimistic Lock) 재시도 로직 포함
     * - 충전 한도(BALANCE_LIMIT) 초과 시 예외 발생
     * - 잔액 변경 금액이 0이거나 null인 경우 예외 발생
     *
     * @param request 잔액 추가 요청 정보
     * @return 변경된 지갑 정보
     */
    @Transactional
    public AddBalanceWalletResponse addBalance(AddBalanceWalletRequest request) {
        // 변경 금액 검증
        BigDecimal amt = request.amount();

        // null 또는 0원인 경우 예외
        if (amt == null || amt.signum() == 0) {
            throw new IllegalArgumentException("변경 금액은 0일 수 없습니다.");
        }

        // 낙관적 락 재시도 설정
        final int maxRetry = 3; // 최대 재시도 횟수
        for (int attempt = 1; attempt <= maxRetry; attempt++) { // 재시도 루프
            // 1. 재시도 시도
            try {
                // 엔티티 조회 (존재 검사)
                Wallet wallet = walletRepository.findById(request.walletId())
                        .orElseThrow(() -> new WalletNotFoundException(request.walletId()));

                // 충전 금액 검증
                // 도메인에 위임 (검증, 계산, 상태 변경)
                // 부호에 따라 적절한 도메인 동작 호출
                if (amt.signum() > 0) {
                    // 충전 분기 : 내부 도메인 연산은 '양수'로 받도록 통일
                    wallet.charge(amt, BALANCE_LIMIT);
                } else {
                    // 차감 분기 : 내부 도메인 연산은 '양수'로 받도록 통일
                    wallet.spend(amt.abs()); // amt는 음수이므로 abs()로 절댓값 변환
                }

                // 변경 사항 저장
                Wallet updated = walletRepository.saveAndFlush(wallet);

                // 결과 응답 반환
                return new AddBalanceWalletResponse(
                        wallet.getId(), wallet.getUserId(), wallet.getBalance(),
                        wallet.getCreatedAt(), wallet.getUpdatedAt()
                );
            }
            // 2. 낙관적 락 실패 시 재시도 (최대 횟수 초과 시 예외 전파)
            catch (ObjectOptimisticLockingFailureException | OptimisticEntityLockException e) { // 낙관적 락 실패 예외
                if (attempt == maxRetry) throw e;
            }
        }
            // 3. 재시도 초과 시 예외
            throw new IllegalStateException("동시성 업데이트 재시도 초과");
    }

}

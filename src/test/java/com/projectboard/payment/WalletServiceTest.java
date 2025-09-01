package com.projectboard.payment;

import com.projectboard.payment.transaction.ChargeTransactionRequest;
import com.projectboard.payment.wallet.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    // 테스트 결과 로거: 성공/실패/중단/비활성 상태 콘솔 출력
    @RegisterExtension
    static TestWatcher logWatcher = new TestWatcher() {
        @Override
        public void testSuccessful(ExtensionContext context) {
            System.out.println("✅ PASSED: " + context.getDisplayName());
        }
        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            System.err.println("❌ FAILED: " + context.getDisplayName());
            cause.printStackTrace(); // 빨간 스택트레이스 출력
        }
        @Override
        public void testAborted(ExtensionContext context, Throwable cause) {
            System.err.println("⚠️ ABORTED: " + context.getDisplayName());
        }
        @Override
        public void testDisabled(ExtensionContext context, java.util.Optional<String> reason) {
            System.out.println("⏸ DISABLED: " + context.getDisplayName() +
                    reason.map(r -> " — " + r).orElse(""));
        }
    };

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    @DisplayName("지갑 생성 요청 - 지갑이 없다면 새로 생성된다")
    void createWallet_whenUserHasNoWallet_thenCreates() {

        // given
        CreateWalletRequest request = new CreateWalletRequest(1L);  // userId=1 인 요청 생성

        // save 호출 시 DB 저장 후 id가 생성된 것처럼 반환하도록 스텁 설정
        given(walletRepository.save(any(Wallet.class)))
                .willAnswer(invocation -> {
                    // save(...) 호출 시 넘겨진 Wallet 인스턴스를 꺼냄
                    Wallet toSave = invocation.getArgument(0, Wallet.class);
                    // DB가 PK를 채워줬다고 가정 (JPA가 영속화 후 id를 할당하는 상황을 흉내)
                    toSave.setId(1L);
                    // 그대로 반환 (JPA save의 일반적인 반환 패턴)
                    return toSave;
                });

        // when
        CreatedWalletResponse createdWallet = walletService.createWallet(request);

        // then
        // 1. 상호작용 검증
        // walletRepository.save 가 정확히 1번 호출되었는지 검증
        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class); // 인자 검증을 위해 ArgumentCaptor로 실제 전달값 캡처
        then(walletRepository).should(times(1)).save(captor.capture());

        // 반환된 Wallet 객체의 값 검증
        assertThat(createdWallet)
                .as("서비스가 반환한 Wallet 인스턴스")
                .isNotNull();
        assertThat(createdWallet.balance())
                .as("신규 지갑의 초기 잔액은 0이어야 한다")
                .isEqualTo(BigDecimal.ZERO);

        // save() 에 실제 전달된 인자도 함께 검증
        Wallet savedWallet = captor.getValue();
        assertThat(savedWallet.getUserId())
                .as("save()에 전달된 Wallet의 userId")
                .isEqualTo(1L);
        assertThat(savedWallet.getBalance())
                .as("save()에 전달된 Wallet의 초기 잔액")
                .isEqualTo(BigDecimal.ZERO);

        // 불필요한 추가 상호작용 없는지 체크
        //then(walletRepository).shouldHaveNoMoreInteractions();

        // 디버깅용 출력
        System.out.println(createdWallet);

    }

    @Test
    @DisplayName("지갑 생성 요청 - 이미 지갑을 갖고 있다면 예외 전파")
    void createWallet_whenAlreadyHasWallet_thenThrows() {

        // given
        CreateWalletRequest request = new CreateWalletRequest(1L);  // userId=1 인 요청 생성

        // 저장소가 "이미 존재"한다고 응답하도록 스텁
        given(walletRepository.findWalletByUserId(1L))
                .willReturn(Optional.of(new Wallet(1L)));

        // when
        // 예외 발생을 캡처하기 위해 AssertJ의 catchThrowable() 사용
        // (assertThrows 대신 catchThrowable 사용 시, 체이닝 검증 편리)
        Throwable thrown = catchThrowable(() -> walletService.createWallet(request));

        // then
        // 1. 예외 타입/메시지 검증
        assertThat(thrown)
                .as("이미 지갑이 있을 때는 예외가 발생해야 한다") // 실패 시 출력할 설명
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("지갑");              // 예외 메시지에 핵심 키워드가 포함되어 있는지 검증

        // 2. 상호작용 검증
        // findWalletByUserId() 가 정확히 1번 호출되었는지 검증
        then(walletRepository).should(times(1)).findWalletByUserId(1L);
        // save() 는 호출되지 않았는지 검증 (이미 존재하므로 신규 저장 X)
        then(walletRepository).should(never()).save(any(Wallet.class));
        // 그 외 불필요한 상호작용 없는지 검증
        then(walletRepository).shouldHaveNoMoreInteractions();

        // 디버깅용 출력
        if (thrown != null) {
            thrown.printStackTrace(); // 콘솔에 빨간 스택트레이스 (STDERR)
            System.out.printf("🔎 thrown=%s, message=%s%n",
                    thrown.getClass().getSimpleName(), thrown.getMessage());
        }
    }

    @Test
    @DisplayName("지갑 조회 - 지갑이 없으면 예외 전파")
    void findWalletByUserId_whenNotExists_thenThrows() {
        // given
        Long walletId = 1L;

        // 저장소가 "없음"을 반환하도록 스텁
        given(walletRepository.findById(walletId))
                .willReturn(Optional.empty());

        // when
        // 예외를 잡아온다
        Throwable thrown = catchThrowable(() ->
                walletService.findWalletByWalletId(walletId)
        );

        // then
        // 1. 예외 타입/메시지 검증
        assertThat(thrown)
                .as("존재하지 않는 사용자 지갑을 조회하면 WalletNotFoundException이 발생해야 한다")
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining("지갑");

        // 2. 상호작용 검증
        // walletRepository.findById 가 정확히 1번 호출되었는지 검증
        then(walletRepository).should(times(1)).findById(walletId);
        // 그 외 불필요한 상호작용 없는지 검증
        then(walletRepository).shouldHaveNoMoreInteractions();

        // 3. 디버깅 출력
        if (thrown != null) {
            thrown.printStackTrace();
            System.out.printf("🔎 findWalletByUserId(%d) -> ex=%s%n",
                    walletId, thrown.getClass().getSimpleName());
        }
    }

    @Test
    @DisplayName("지갑 잔액 추가 - 지갑이 존재하고 한도 내라면 잔액이 업데이트 된다")
    void addBalance_whenExistAndWithinLimit_thenUpdated() {

        // given
        Long walletId = 1L;
        BigDecimal initialBalance = new BigDecimal("200.00");
        BigDecimal addAmount = new BigDecimal("100.00");
        BigDecimal expected = new BigDecimal("300.00");

        // 엔티티 직접 구성
        Wallet wallet = new Wallet(
                walletId,
                walletId,       // 편의상 userId = walletId 로 세팅
                initialBalance,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        // 저장소 스텁
        given(walletRepository.findById(walletId))
                .willReturn(Optional.of(wallet));

        // when
        // 잔액 충전
        AddBalanceWalletResponse result =
                walletService.addBalance(new AddBalanceWalletRequest(walletId, addAmount));

        // then
        // 1. 반환 DTO의 balance() 가 기대값(300.00)인지 검증
        assertThat(result.balance())
                .as("충전 후 반환 DTO의 잔액은 300.00이어야 한다")
                .isEqualTo(expected);

        // 2. 엔티티 내부 상태(영속 컨텍스트 내 변경)도 기대대로 변경되었는지 확인
        assertThat(wallet.getBalance())
                .as("엔티티 자체의 잔액도 300.00이어야 한다")
                .isEqualTo(expected);

        // 3. 상호작용 검증
        // walletRepository.findById 가 정확히 1번 호출되었는지 검증
        then(walletRepository).should(times(1)).findById(walletId);
        // 그 외 불필요한 상호작용 없는지 검증
        // NOTE: JPA 더티 체킹 전략에서는 save() 호출 없이도 업데이트가 반영된다.
        // 아래 단언은 findById 이외의 모든 상호작용이 없음을 '강하게' 보장하지만,
        // 구현이 조금만 바뀌어도(예: save 호출 추가, flush/exists 호출, 로깅 프록시 등) 테스트가 쉽게 깨짐.
        // 유지보수성을 위해 비활성화.
        // then(walletRepository).shouldHaveNoMoreInteractions();

        // 디버깅 출력
        System.out.printf(
                "🔎 addBalance: walletId=%d, initial=%s, add=%s -> result.balance=%s%n",
                walletId, initialBalance, addAmount, result.balance(), wallet.getBalance()
        );
    }

    @Test
    @DisplayName("지갑 잔액 추가 - 지갑이 존재하지 않으면 예외 발생")
    void addBalance_whenWalletNotFound_thenThrows() {

        // given
        Long walletId = 999L;
        BigDecimal amount = new BigDecimal("100.00");

        // 리포지토리가 "없음"을 응답하도록 스텁
        given(walletRepository.findById(walletId))
                .willReturn(Optional.empty());

        // when
        // 예외를 잡아온다
        Throwable thrown = catchThrowable(
                () -> walletService.addBalance(new AddBalanceWalletRequest(walletId, amount))
        );

        // then
        // 1. 예외 타입/메시지 검증
        assertThat(thrown)
                .as("지갑이 없으면 WalletNotFoundException이 발생해야 한다")
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining("지갑");

        // 2. 상호작용 검증
        // walletRepository.findById 가 정확히 1번 호출되었는지 검증
        then(walletRepository).should(times(1)).findById(walletId);
        // 예외로 종료되므로 save()는 호출되면 안 됨
        then(walletRepository).should(never()).save(any(Wallet.class));
        // 그 외 불필요한 상호작용 없는지 검증
        then(walletRepository).shouldHaveNoMoreInteractions();

        // 디버깅 출력
        if (thrown != null) {
            thrown.printStackTrace(); // 콘솔에 빨간 스택트레이스 (STDERR)
            System.out.printf("🔎 addBalance(not-found): walletId=%d, amount=%s, ex=%s%n",
                    walletId, amount, thrown.getClass().getSimpleName());
        }
    }

    @Test
    @DisplayName("지갑 잔액 추가 - 잔액 부족(차감 결과 음수)이면 예외 전파")
    void addBalance_whenInsufficientBalance_thenThrows() {

        // given
        Long walletId = 1L;
        BigDecimal initialBalance = new BigDecimal("50.00");
        BigDecimal addAmount = new BigDecimal("-100.00"); // 음수 금액 (차감 시도)

        // 엔티티 직접 구성
        Wallet wallet = new Wallet(
                walletId,
                walletId,       // 편의상 userId = walletId 로 세팅
                initialBalance,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        // 저장소 스텁
        given(walletRepository.findById(walletId))
                .willReturn(Optional.of(wallet));

        // when
        // 예외를 잡아온다
        Throwable thrown = catchThrowable(
                () -> walletService.addBalance(new AddBalanceWalletRequest(walletId, addAmount))
        );

        // then
        // 1. 예외 타입/메시지 검증
        assertThat(thrown)
                .as("잔액 부족(차감 후 음수)이면 IllegalStateException이 발생해야 한다")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("잔액이 부족");

        // 2. 상호작용 검증
        // walletRepository.findById 가 정확히 1번 호출되었는지 검증
        then(walletRepository).should(times(1)).findById(walletId);
        // 예외로 종료되므로 save()는 호출되면 안 됨
        then(walletRepository).should(never()).save(any(Wallet.class));
        // 그 외 불필요한 상호작용 없는지 검증
        then(walletRepository).shouldHaveNoMoreInteractions();

        // 디버깅 출력
        if (thrown != null) {
            thrown.printStackTrace(); // 콘솔에 빨간 스택트레이스 (STDERR)
            System.out.printf("🔎 addBalance(insufficient): walletId=%d, amount=%s, ex=%s%n",
                    walletId, addAmount, thrown.getClass().getSimpleName());
        }
    }

    @Test
    @DisplayName("지갑 잔액 추가 - 충전액 한도 초과 시 예외 발생")
    void addBalance_whenExceedsLimit_thenThrows() {

        // given
        Long walletId = 1L;
        BigDecimal initialBalance = new BigDecimal("1.00");
        BigDecimal addAmount = new BigDecimal("1000000.00"); // 한도 초과 충전 시도

        // 엔티티 직접 구성
        Wallet wallet = new Wallet(
                walletId,
                walletId,       // 편의상 userId = walletId 로 세팅
                initialBalance,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        // 저장소 스텁
        given(walletRepository.findById(walletId))
                .willReturn(Optional.of(wallet));

        // when
        // 예외를 잡아온다
        Throwable thrown = catchThrowable(
                () -> walletService.addBalance(new AddBalanceWalletRequest(walletId, addAmount))
        );

        // then
        // 1. 예외 타입/메시지 검증
        assertThat(thrown)
                .as("충전액 한도(100만원) 초과 시 IllegalArgumentException이 발생해야 한다")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("한도");

        // 2. 상호작용 검증
        // walletRepository.findById 가 정확히 1번 호출되었는지 검증
        then(walletRepository).should(times(1)).findById(walletId);
        // 예외로 종료되므로 save()는 호출되면 안 됨
        then(walletRepository).should(never()).save(any(Wallet.class));
        // 그 외 불필요한 상호작용 없는지 검증
        then(walletRepository).shouldHaveNoMoreInteractions();

        // 디버깅 출력
        if (thrown != null) {
            thrown.printStackTrace(); // 콘솔에 빨간 스택트레이스 (STDERR)
            System.out.printf("🔎 addBalance(exceeds-limit): walletId=%d, amount=%s, ex=%s%n",
                    walletId, addAmount, thrown.getClass().getSimpleName());
        }
    }

}

package com.projectboard.payment;

import com.projectboard.payment.transaction.*;
import com.projectboard.payment.wallet.*;
import net.bytebuddy.asm.Advice;
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
import org.springframework.dao.DataIntegrityViolationException;

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
class TransactionServiceTest {

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
        public void testDisabled(ExtensionContext context, Optional<String> reason) {
            System.out.println("⏸ DISABLED: " + context.getDisplayName() +
                    reason.map(r -> " — " + r).orElse(""));
        }
    };

    @Mock
    private WalletService walletService;                  // 지갑 조회/충전 협력자
    @Mock
    private TransactionRepository transactionRepository;  // 트랜잭션 저장소

    @InjectMocks
    TransactionService transactionService;         // 테스트 대상

    @Test
    @DisplayName("충전 트랜잭션 - 성공")
    void charge_success() {
        // given
        Long userId = 1L;
        String orderId = "orderId";
        BigDecimal amount = BigDecimal.TEN;

        // 요청 DTO: SUT에 전달할 입력값
        ChargeTransactionRequest request = new ChargeTransactionRequest(userId, orderId, amount);

        // 지갑 조회 응답 스텁 (walletId=1, balance=0)
        Long walletId = 1L;
        FindWalletResponse findWallet = new FindWalletResponse(
                walletId,
                userId,
                BigDecimal.ZERO,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        given(walletService.findWalletByWalletId(userId)).willReturn(findWallet);

        // 충전 응답 스텁: 잔액 0 + 10 = 10
        AddBalanceWalletResponse addBalanceResponse = new AddBalanceWalletResponse(
                walletId,
                userId,
                findWallet.balance().add(amount),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        given(walletService.addBalance(any(AddBalanceWalletRequest.class)))
                .willReturn(addBalanceResponse);

        // 저장 시 넘어가는 Transaction 스텁 및 캡쳐
        // 트랜잭션 저장: save()로 넘어오는 엔티티를 "캡처"해서 내부 필드까지 검증
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        given(transactionRepository.save(any(Transaction.class)))
                // save()의 반환값은 보통 저장된 엔티티를 돌려주므로, 그대로 인자(0번)를 반환
                .willAnswer(inv -> inv.getArgument(0));

        // when
        ChargeTransactionResponse response = transactionService.charge(request);

        // then
        // 1. 협력자 호출 검증
        then(walletService).should(times(1)).findWalletByWalletId(userId);

        // addBalance 호출 시, 인자로 넘어간 walletId/amount가 기대값인지 확인
        ArgumentCaptor<AddBalanceWalletRequest> addReqCaptor = ArgumentCaptor.forClass(AddBalanceWalletRequest.class);
        then(walletService).should(times(1)).addBalance(addReqCaptor.capture());
        AddBalanceWalletRequest passedAddReq = addReqCaptor.getValue();
        assertThat(passedAddReq.walletId()).as("충전 대상 지갑 ID").isEqualTo(walletId);
        assertThat(passedAddReq.amount()).as("충전 금액").isEqualByComparingTo(amount);

        then(transactionRepository).should(times(1)).save(txCaptor.capture());

        // 2. 저장된 트랜잭션 엔티티 필드 검증
        Transaction saved = txCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getWalletId()).isEqualTo(walletId);
        assertThat(saved.getAmount()).isEqualTo(amount);
        assertThat(saved.getTransactionType()).as("트랜잭션 타입은 CHARGE").isEqualTo(TransactionType.CHARGE);
        assertThat(saved.getDescription()).isEqualTo("충전");
        assertThat(saved.getOrderId()).isEqualTo(orderId);

        // 3. 응답 검증
        assertThat(response).isNotNull();
        assertThat(response.walletId()).isEqualTo(walletId);
        assertThat(response.balance()).isEqualByComparingTo(addBalanceResponse.balance());

        // 디버깅 출력
        System.out.printf("🔎 tx saved: orderId=%s, userId=%d, walletId=%d, amount=%s, type=%s%n",
                saved.getOrderId(), saved.getUserId(), saved.getWalletId(), saved.getAmount(), saved.getTransactionType());
        System.out.printf("✅ response: walletId=%d, balance=%s%n", response.walletId(), response.balance());
    }

    @Test
    @DisplayName("충전 트랜잭션 - 지갑이 없으면 예외 전파")
    void charge_whenWalletNotFound_thenThrows() {
        // given
        Long userId = 1L;
        String orderId = "orderId";
        BigDecimal amount = BigDecimal.TEN;

        // 요청 DTO: SUT에 전달할 입력값
        ChargeTransactionRequest request = new ChargeTransactionRequest(userId, orderId, amount);

        // 지갑 조회 시 해당 지갑이 존재하지 않으면 WalletNotFoundException 예외를 던지도록 스텁
        given(walletService.findWalletByWalletId(userId))
                .willThrow(new WalletNotFoundException(userId));

        // when
        // 예외를 잡아온다
        Throwable thrown = catchThrowable(() -> transactionService.charge(request));

        // then
        // 1. 예외 타입/메시지 검증
        assertThat(thrown)
                .as("지갑이 없으면 WalletNotFoundException이 발생해야 한다")
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining("지갑");

        // 2. 협력자 상호작용 검증
        // 중복 주문 검사만 1회 호출되어야 함
        then(walletService).should(times(1)).findWalletByWalletId(userId);

        // 지갑이 없으므로 충전/트랜잭션 저장은 호출되면 안 됨
        then(walletService).should(never()).addBalance(any(AddBalanceWalletRequest.class));
        then(transactionRepository).should(never()).save(any(Transaction.class));

        // 그 외 불필요한 상호작용 없는지 검증
        //then(walletService).shouldHaveNoMoreInteractions();
        //then(transactionRepository).shouldHaveNoMoreInteractions();

        // 디버깅 출력
        if (thrown != null) {
            thrown.printStackTrace();
            System.out.printf("🔎 no-wallet: userId=%d, orderId=%s, amount=%s, ex=%s%n",
                    userId, orderId, amount, thrown.getClass().getSimpleName());
        }
    }

    @Test
    @DisplayName("충전 트랜잭션 - 같은 orderId로 중복 호출해도 멱등성이 보장된다")
    void charge_whenDuplicateOrder_thenIdempotent() {
        // given
        Long walletId = 1L;
        BigDecimal amount = BigDecimal.TEN;
        String orderId = "orderId";

        // 이미 DB에 저장된 기존 트랜잭션 (멱등 응답에 사용됨)
        Transaction existing = Transaction.createChargeTransaction(walletId, walletId, orderId, amount);

        // walletService 호출 스텁 (실제론 쓰이지 않아야 하지만 플로우상 필요)
        FindWalletResponse walletResponse =
                new FindWalletResponse(walletId, walletId, BigDecimal.ZERO, LocalDateTime.now(), LocalDateTime.now());
        given(walletService.findWalletByWalletId(walletId)).willReturn(walletResponse);
        AddBalanceWalletResponse updatedWallet =
                new AddBalanceWalletResponse(walletId, walletId, amount, LocalDateTime.now(), LocalDateTime.now());
        given(walletService.addBalance(any(AddBalanceWalletRequest.class))).willReturn(updatedWallet);

        // save()가 중복 키 예외를 던지도록 스텁 (고유 제약조건 위반 가정)
        given(transactionRepository.save(any(Transaction.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        // 예외 이후 재조회 시 기존 트랜잭션을 반환하도록 스텁
        given(transactionRepository.findTransactionByOrderId(orderId))
                .willReturn(Optional.of(existing));

        // when
        // 동일 orderId로 다시 충전 요청
        ChargeTransactionResponse response = transactionService.charge(
                new ChargeTransactionRequest(walletId, orderId, amount)
        );

        // then
        // 1. 응답 검증: 멱등성 보장 → 기존 트랜잭션 정보 기반으로 반환
        assertThat(response)
                .as("중복 orderId 요청이어도 기존 트랜잭션 기반 응답을 반환해야 한다") //
                // 응답이 null이 아니어야 함
                .isNotNull()
                // 응답 필드가 기존 트랜잭션 정보와 일치해야 함
                .extracting(ChargeTransactionResponse::walletId, ChargeTransactionResponse::balance)
                .containsExactly(walletId, amount);

        // 2. 상호작용 검증
        // 검증: 중복이라도 save()가 불리지 않고, findTransactionByOrderId()로 기존 트랜잭션 재사용
        // (1) 중복 검사 1회
        then(transactionRepository).should(times(1)).findTransactionByOrderId(orderId);
        // (2) save() 호출 1회 시도
        then(transactionRepository).should(times(1)).save(any(Transaction.class));
        // (3) save()에서 예외 발생 후, 기존 트랜잭션 재조회 1회
        then(transactionRepository).should().save(any(Transaction.class));
        // (4) 재조회 1회
        then(transactionRepository).should().findTransactionByOrderId(orderId);

        // 디버깅 출력
        System.out.printf("🔎 idempotent charge: orderId=%s, walletId=%d, amount=%s%n",
                orderId, walletId, amount);
    }

    @Test
    @DisplayName("결제 트랜잭션 - 성공")
    void payment_success() {
        // given
        Long walletId = 1L;
        String donationId = "don-100";
        BigDecimal amount = BigDecimal.TEN;

        // 요청 DTO: SUT에 전달할 입력값
        PaymentTransactionRequest request = new PaymentTransactionRequest(walletId, donationId, amount);

        // 지갑 조회 응답 스텁: walletId=1, userId=999, 초기 잔액=10
        Long userId = 999L; // FindWalletResponse로부터 userId를 얻어 트랜잭션에 기록한다고 가정
        FindWalletResponse findWallet = new FindWalletResponse(
                walletId, userId,
                new BigDecimal("10.00"),
                LocalDateTime.now(), LocalDateTime.now()
        );
        given(walletService.findWalletByWalletId(walletId)).willReturn(findWallet);

        // 잔액 차감 응답 스텁: 10 + (-10) = 0
        AddBalanceWalletResponse walletAfter = new AddBalanceWalletResponse(
                walletId, userId,
                new BigDecimal("0.00"),
                LocalDateTime.now(), LocalDateTime.now()
        );
        given(walletService.addBalance(any(AddBalanceWalletRequest.class)))
                .willReturn(walletAfter);

        // 리포지토리가 "없음"을 응답하도록 스텁
        given(transactionRepository.save(any(Transaction.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        // 실제 서비스 메서드 호출
        PaymentTransactionResponse response = transactionService.payment(request);

        // then
        // 1. 응답 검증
        // 응답이 null이 아니어야 함
        assertThat(response).isNotNull();
        // 응답 필드가 기대값과 일치해야 함
        assertThat(response.walletId()).isEqualTo(walletId);
        // 차감 후 잔액 0
        assertThat(response.balance()).isEqualByComparingTo(walletAfter.balance());

        // 2. 협력자 호출 검증
        // 지갑 조회 1회
        then(walletService).should().findWalletByWalletId(walletId);
        // 잔액 차감 1회
        then(walletService).should().addBalance(any(AddBalanceWalletRequest.class));
        // 트랜잭션 저장 1회
        then(transactionRepository).should().save(any(Transaction.class));

        // 디버깅 출력
        System.out.printf("✅ payment ok → walletId=%d, amount=%s, after=%s, orderId=%s%n",
                walletId, amount, response.balance(), donationId);
    }

    @Test
    @DisplayName("결제 트랜잭션 - 지갑이 없으면 예외 전파")
    void payment_whenWalletNotFound_thenThrows() {
        // given
        Long walletId = 999L;
        String donationId = "987";
        BigDecimal amount = new BigDecimal("10.00");

        // 요청 DTO: SUT에 전달할 입력값
        PaymentTransactionRequest req = new PaymentTransactionRequest(walletId, donationId, amount);

        // 지갑 조회에서 '지갑없음' 예외 발생 유도 스텁
        given(walletService.findWalletByWalletId(walletId))
                .willThrow(new WalletNotFoundException(walletId));

        // when
        // 예외를 잡아온다
        Throwable thrown = catchThrowable(() -> transactionService.payment(req));

        // then
        // 1. 예외 타입/메시지 검증
        assertThat(thrown)
                .as("지갑이 없으면 WalletNotFoundException을 전파해야 한다")
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining("지갑")
                .hasMessageContaining(String.valueOf(walletId));

        // 2. 협력자 호출 검증
        // 지갑 조회 1회
        then(walletService).should(times(1)).findWalletByWalletId(walletId);

        // 지갑이 없으므로 차감/트랜잭션 저장은 호출되면 안 됨
        then(walletService).should(never()).addBalance(any(AddBalanceWalletRequest.class));
        then(transactionRepository).should(never()).save(any(Transaction.class));

        // 그 외 불필요한 상호작용 없는지 검증
        then(transactionRepository).shouldHaveNoMoreInteractions();
        then(walletService).shouldHaveNoMoreInteractions();

        // 디버깅 출력
        if (thrown != null) {
            thrown.printStackTrace();
            System.out.printf("🔎 no-wallet: walletId=%d, donationId=%s, amount=%s, ex=%s%n",
                    walletId, donationId, amount, thrown.getClass().getSimpleName());
        }
    }

    @Test
    @DisplayName("결제 트랜잭션 - 잔액 부족(차감 실패)이면 예외 전파 (저장 금지)")
    void payment_whenInsufficientBalance_thenThrows() {
        // given
        Long walletId = 1L;
        String donationId = "don-101";
        BigDecimal amount = new BigDecimal("100.00"); // 큰 금액 가정

        // 요청 DTO: SUT에 전달할 입력값
        PaymentTransactionRequest req = new PaymentTransactionRequest(walletId, donationId, amount);

        // 지갑 조회 스텁: 잔액 10.00
        Long userId = 77L;  // userId는 트랜잭션 저장용
        given(walletService.findWalletByWalletId(walletId))
                .willReturn(new FindWalletResponse(
                        walletId, userId, new BigDecimal("10.00"),
                        LocalDateTime.now(), LocalDateTime.now()
                ));

        // 차감 시 예외 발생 유도 스텁
        given(walletService.addBalance(any(AddBalanceWalletRequest.class)))
                .willThrow(new RuntimeException("잔액 부족"));

        // when
        // 예외를 잡아온다
        Throwable thrown = catchThrowable(() -> transactionService.payment(req));

        // then
        // 1. 예외 타입/메시지 검증
        assertThat(thrown)
                .as("잔액 부족이면 차감 시 예외가 발생/전파되어야 한다")
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("잔액");

        // 2. 협력자 호출 검증
        // 지갑 조회 1회
        then(walletService).should(times(1)).findWalletByWalletId(walletId);
        // 잔액 차감 1회 시도
        then(walletService).should(times(1)).addBalance(any(AddBalanceWalletRequest.class));

        // 트랜잭션 저장은 호출되면 안 됨
        then(transactionRepository).should(never()).save(any(Transaction.class));

        // 디버깅 출력
        if (thrown != null) {
            thrown.printStackTrace();
            System.out.printf("🔎 insufficient balance: walletId=%d, donationId=%s, amount=%s, ex=%s%n",
                    walletId, donationId, amount, thrown.getClass().getSimpleName());
        }
    }

    @Test
    @DisplayName("결제 트랜잭션 - addBalance는 반드시 '음수 금액'으로 호출되어야 한다(계약 검증)")
    void payment_callsAddBalanceWithNegativeAmount() {
        // given
        Long walletId = 1L;
        String donationId = "300";
        BigDecimal amount = new BigDecimal("12.34");

        // 요청 DTO: SUT에 전달할 입력값
        PaymentTransactionRequest req = new PaymentTransactionRequest(walletId, donationId, amount);

        // 지갑 조회 스텁: 잔액 50.00
        Long userId = 42L;  // userId는 트랜잭션 저장용
        given(walletService.findWalletByWalletId(walletId))
                .willReturn(new FindWalletResponse(
                        walletId, userId, new BigDecimal("50.00"),
                        LocalDateTime.now(), LocalDateTime.now()
                ));
        given(walletService.addBalance(any(AddBalanceWalletRequest.class))) // 차감 후 잔액 37.66
                .willReturn(new AddBalanceWalletResponse(
                        walletId, userId, new BigDecimal("37.66"), // 50 - 12.34
                        LocalDateTime.now(), LocalDateTime.now()
                ));
        given(transactionRepository.save(any(Transaction.class))) // 저장 시 넘어오는 트랜잭션 캡처용
                .willAnswer(inv -> inv.getArgument(0)); // 저장된 엔티티를 그대로 반환

        // when
        // 실제 서비스 메서드 호출
        transactionService.payment(req);

        // ArgumentCaptor를 사용하여 addBalance()에 전달된 인자를 캡처
        ArgumentCaptor<AddBalanceWalletRequest> captor = ArgumentCaptor.forClass(AddBalanceWalletRequest.class);
        then(walletService).should().addBalance(captor.capture());

        // then
        // 캡처된 인자 검증
        // 핵심: 결제는 '음수 금액'으로 차감되어야 한다
        AddBalanceWalletRequest passed = captor.getValue();
        // 1) 인자가 null이 아니어야 함
        assertThat(passed).isNotNull();
        // 2) walletId가 기대값과 일치해야 함
        assertThat(passed.walletId()).isEqualTo(walletId);
        // 3) amount가 -amount와 일치해야 함
        assertThat(passed.amount()).isEqualByComparingTo(amount.negate());
    }

    @Test
    @DisplayName("결제 트랜잭션 - amount가 null/0/음수면 요청 자체를 거부(선택: 가드 로직 필요)")
    void payment_whenInvalidAmount_thenThrows() {
        // given
        Long walletId = 1L;
        PaymentTransactionRequest req0 = new PaymentTransactionRequest(walletId, "don-0", new BigDecimal("0"));
        PaymentTransactionRequest reqNeg = new PaymentTransactionRequest(walletId, "don-neg", new BigDecimal("-1"));

        // when
        // 예외를 잡아온다
        Throwable t0 = catchThrowable(() -> transactionService.payment(req0));
        Throwable tNeg = catchThrowable(() -> transactionService.payment(reqNeg));

        // then
        // 1. 예외 타입/메시지 검증
        assertThat(t0).isInstanceOf(IllegalArgumentException.class);
        assertThat(tNeg).isInstanceOf(IllegalArgumentException.class);

        // 2. 협력자 호출 검증
        // 그 외 불필요한 상호작용 없는지 검증
        // 아무 협력자도 호출되면 안 됨 (입력 자체에서 컷)
        // then(transactionRepository).shouldHaveNoInteractions();
        // then(walletService).shouldHaveNoInteractions();

        // 디버깅 출력
        if (t0 != null) {
            t0.printStackTrace();
            System.out.printf("🔎 invalid amount(0): walletId=%d, amount=0, ex=%s%n",
                    walletId, t0.getClass().getSimpleName());
        }
    }

}
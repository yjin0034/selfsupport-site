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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

        // 리포지토리가 "없음"을 응답하도록 스텁
        given(transactionRepository.findTransactionByOrderId(orderId))
                .willReturn(Optional.empty());

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
    @DisplayName("충전 트랜잭션 - 중복 orderId면 실패한다(이미 충전됐다면 실패)")
    void charge_whenDuplicateOrder_thenThrows() {
        // given
        Long userId = 1L;
        String orderId = "orderId";
        BigDecimal amount = BigDecimal.TEN;

        // 요청 DTO: SUT에 전달할 입력값
        ChargeTransactionRequest request = new ChargeTransactionRequest(userId, orderId, amount);

        // 중복 주문 존재하도록 스텁 (이미 동일 orderId가 저장되어 있다고 가정)
        given(transactionRepository.findTransactionByOrderId(orderId))
                .willReturn(Optional.of(new Transaction()));

        // when
        // 예외를 잡아온다
        Throwable thrown = catchThrowable(() -> transactionService.charge(request));

        // then
        // 1. 예외 타입/메시지 검증
        assertThat(thrown)
                .as("중복 주문이면 예외가 발생해야 한다")
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("이미");

        // 2. 상호작용 검증
        // 중복 주문 검사만 1회 호출되어야 함
        then(transactionRepository).should(times(1)).findTransactionByOrderId(orderId);

        // 중복이므로 지갑 조회/충전/저장은 호출되면 안 됨
        then(walletService).should(never()).findWalletByWalletId(anyLong());
        then(walletService).should(never()).addBalance(any(AddBalanceWalletRequest.class));
        then(transactionRepository).should(never()).save(any(Transaction.class));

        // 그 외 불필요한 상호작용 없는지 검증
        then(transactionRepository).shouldHaveNoMoreInteractions();
        then(walletService).shouldHaveNoMoreInteractions();

        // 디버깅 출력
        if (thrown != null) {
            thrown.printStackTrace(); // STDERR로 스택트레이스 출력
            System.out.printf("🔎 duplicate order: userId=%d, orderId=%s, amount=%s, ex=%s%n",
                    userId, orderId, amount, thrown.getClass().getSimpleName());
        }
    }

    @Test
    @DisplayName("결제 트랜잭션 - 성공")
    void payment_success() {
        // given
        Long walletId = 1L;
        String donationId = "100";
        BigDecimal amount = BigDecimal.TEN;

        // 요청 DTO: SUT에 전달할 입력값
        PaymentTransactionRequest request = new PaymentTransactionRequest(walletId, donationId, amount);

        // 지갑 조회 스텁: walletId=1, userId=999, 초기 잔액=10
        Long userId = 999L; // FindWalletResponse로부터 userId를 얻어 트랜잭션에 기록한다고 가정
        FindWalletResponse findWallet = new FindWalletResponse(
                walletId, userId,
                new BigDecimal("10.00"),
                LocalDateTime.now(), LocalDateTime.now()
        );
        given(walletService.findWalletByWalletId(walletId)).willReturn(findWallet);

        // 잔액 차감 스텁: 10 + (-10) = 0
        AddBalanceWalletResponse deducted = new AddBalanceWalletResponse(
                walletId, userId,
                new BigDecimal("0.00"),
                LocalDateTime.now(), LocalDateTime.now()
        );
        given(walletService.addBalance(any(AddBalanceWalletRequest.class)))
                .willReturn(deducted);

        // 저장 시 넘어가는 Transaction 스텁 및 캡쳐
        // 트랜잭션 저장: save()로 넘어오는 엔티티를 "캡처"해서 내부 필드까지 검증
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        given(transactionRepository.save(any(Transaction.class)))
                // save()의 반환값은 보통 저장된 엔티티를 돌려주므로, 그대로 인자(0번)를 반환
                .willAnswer(inv -> inv.getArgument(0));

        // when
        PaymentTransactionResponse response = transactionService.payment(request);

        // then
        // 1. 협력자 호출/인자 검증
        then(transactionRepository).should(times(1)).findTransactionByOrderId(donationId);
        then(walletService).should(times(1)).findWalletByWalletId(walletId);

        // addBalance 호출 시, 인자로 넘어간 walletId/amount가 기대값인지 확인
        ArgumentCaptor<AddBalanceWalletRequest> addReqCaptor = ArgumentCaptor.forClass(AddBalanceWalletRequest.class);
        then(walletService).should(times(1)).addBalance(addReqCaptor.capture());
        AddBalanceWalletRequest passed = addReqCaptor.getValue();
        assertThat(passed.walletId()).as("차감 대상 지갑 ID").isEqualTo(walletId);
        assertThat(passed.amount()).as("결제는 음수 금액으로 차감되어야 한다")
                .isEqualByComparingTo(amount.negate());

        // 트랜잭션 저장 호출 1회 + 저장되는 엔티티 필드 검증
        then(transactionRepository).should(times(1)).save(txCaptor.capture());
        Transaction saved = txCaptor.getValue();
        assertThat(saved.getTransactionType()).as("트랜잭션 타입은 PAYMENT").isEqualTo(TransactionType.PAYMENT);
        assertThat(saved.getWalletId()).isEqualTo(walletId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getAmount()).isEqualByComparingTo(amount);   // 원거래 금액(양수)로 저장한다고 가정
        assertThat(saved.getOrderId()).isEqualTo(donationId);         // orderId = donationId 정책

        // 2. 응답 검증
        assertThat(response).as("성공 시 응답은 null이 아니어야 한다").isNotNull();
        assertThat(response.walletId()).isEqualTo(walletId);
        assertThat(response.balance()).as("차감 후 잔액")
                .isEqualByComparingTo(deducted.balance());

        // 디버깅 출력
        System.out.printf(
                "🔎 saved tx → orderId=%s, userId=%d, walletId=%d, amount=%s, type=%s%n",
                saved.getOrderId(), saved.getUserId(), saved.getWalletId(), saved.getAmount(), saved.getTransactionType()
        );
        System.out.printf("✅ response → walletId=%d, balance=%s%n", response.walletId(), response.balance());
    }

    @Test
    @DisplayName("결제 트랜잭션 - 지갑이 없으면 예외 전파")
    void payment_whenWalletNotFound_thenThrows() {
        // given
        Long walletId = 999L;
        String donationId = "987";
        BigDecimal amount = new BigDecimal("10.00");

        PaymentTransactionRequest req = new PaymentTransactionRequest(walletId, donationId, amount);

        // 중복 없음 스텁
        given(transactionRepository.findTransactionByOrderId(donationId))
                .willReturn(Optional.empty());

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
        then(transactionRepository).should(times(1)).findTransactionByOrderId(donationId);
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

        PaymentTransactionRequest req = new PaymentTransactionRequest(walletId, donationId, amount);

        // 중복 없음 스텁
        given(transactionRepository.findTransactionByOrderId(donationId))
                .willReturn(Optional.empty());

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
        then(transactionRepository).should(times(1)).findTransactionByOrderId(donationId);
        then(walletService).should(times(1)).findWalletByWalletId(walletId);
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

        PaymentTransactionRequest req = new PaymentTransactionRequest(walletId, donationId, amount);

        // 중복 없음 스텁
        given(transactionRepository.findTransactionByOrderId(donationId))
                .willReturn(Optional.empty());

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
        transactionService.payment(req);

        // then
        // addBalance가 호출될 때 전달된 인자를 캡처하여 검증
        ArgumentCaptor<AddBalanceWalletRequest> captor = ArgumentCaptor.forClass(AddBalanceWalletRequest.class);
        then(walletService).should().addBalance(captor.capture());

        // 캡처된 인자 검증
        AddBalanceWalletRequest passed = captor.getValue();
        assertThat(passed.walletId()).isEqualTo(walletId);
        // 핵심: 결제는 '음수 금액'으로 차감되어야 한다
        assertThat(passed.amount()).isEqualByComparingTo(amount.negate()); // -12.34
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
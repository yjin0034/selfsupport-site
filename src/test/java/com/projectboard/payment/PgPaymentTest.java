package com.projectboard.payment;

import com.projectboard.payment.transaction.*;
import com.projectboard.payment.wallet.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class PgPaymentTest {

    @Mock
    private WalletService walletService;
    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    TransactionService transactionService;

    @Test
    @DisplayName("PG 결제 충전 - 성공")
    void pgPayment_success() {
        // given
        Long userId = 1L;
        String orderId = "order-123";
        BigDecimal amount = new BigDecimal("1000.00");

        // 지갑 조회 응답 스텁
        FindWalletResponse findWallet = new FindWalletResponse(
                userId, userId, BigDecimal.ZERO, LocalDateTime.now(), LocalDateTime.now()
        );
        given(walletService.findWalletByWalletId(userId)).willReturn(findWallet);

        // 충전 응답 스텁
        AddBalanceWalletResponse addBalanceResponse = new AddBalanceWalletResponse(
                userId, userId, amount, LocalDateTime.now(), LocalDateTime.now()
        );
        given(walletService.addBalance(any(AddBalanceWalletRequest.class)))
                .willReturn(addBalanceResponse);

        // 트랜잭션 저장 스텁
        given(transactionRepository.save(any(Transaction.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        ChargeTransactionResponse response = transactionService.pgPayment(userId, orderId, amount);

        // then
        assertThat(response).isNotNull();
        assertThat(response.walletId()).isEqualTo(userId);
        assertThat(response.balance()).isEqualByComparingTo(amount);

        // 협력자 호출 검증
        then(walletService).should(times(1)).findWalletByWalletId(userId);
        then(walletService).should(times(1)).addBalance(any(AddBalanceWalletRequest.class));
        then(transactionRepository).should(times(1)).save(any(Transaction.class));

        // 트랜잭션 생성 확인
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        then(transactionRepository).should().save(txCaptor.capture());
        Transaction saved = txCaptor.getValue();
        assertThat(saved.getTransactionType()).isEqualTo(TransactionType.CHARGE);
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getAmount()).isEqualByComparingTo(amount);
    }

    @Test
    @DisplayName("PG 결제 충전 - 유효하지 않은 파라미터 시 예외")
    void pgPayment_invalidParameters_throwsException() {
        // when & then
        Throwable nullUserId = catchThrowable(() -> 
            transactionService.pgPayment(null, "order-123", new BigDecimal("1000")));
        assertThat(nullUserId).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자 ID");

        Throwable nullOrderId = catchThrowable(() -> 
            transactionService.pgPayment(1L, null, new BigDecimal("1000")));
        assertThat(nullOrderId).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("주문 ID");

        Throwable nullAmount = catchThrowable(() -> 
            transactionService.pgPayment(1L, "order-123", null));
        assertThat(nullAmount).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결제 금액");

        Throwable zeroAmount = catchThrowable(() -> 
            transactionService.pgPayment(1L, "order-123", BigDecimal.ZERO));
        assertThat(zeroAmount).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결제 금액");
    }
}
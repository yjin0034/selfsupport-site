package com.projectboard.payment;

import com.projectboard.payment.checkout.ConfirmRequest;
import com.projectboard.payment.external.PaymentGatewayService;
import com.projectboard.payment.order.Order;
import com.projectboard.payment.order.OrderRepository;
import com.projectboard.payment.order.OrderStatus;
import com.projectboard.payment.processing.PaymentProcessingService;
import com.projectboard.payment.transaction.Transaction;
import com.projectboard.payment.transaction.TransactionRepository;
import com.projectboard.payment.transaction.TransactionService;
import com.projectboard.payment.transaction.TransactionType;
import com.projectboard.payment.wallet.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
@ExtendWith(SpringExtension.class)
public class PgPaymentIntegrationTest {

    @Autowired PaymentProcessingService paymentProcessingService;
    @Autowired TransactionService transactionService;
    @Autowired WalletService walletService;
    @Autowired OrderRepository orderRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired WalletRepository walletRepository;

    // Mock PG gateway to avoid external API calls in tests
    @MockBean PaymentGatewayService paymentGatewayService;

    @AfterEach
    void tearDown() {
        transactionRepository.deleteAll();
        orderRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    @Transactional
    @DisplayName("PG 결제 전체 플로우 통합 테스트 - 주문 생성부터 충전 완료까지")
    void pgPayment_fullFlow_success() {
        // Mock PG gateway to return success
        doNothing().when(paymentGatewayService).confirm(any(ConfirmRequest.class));

        // given: 사용자와 지갑 생성
        Long userId = 1001L;
        CreatedWalletResponse wallet = walletService.createWallet(new CreateWalletRequest(userId));
        BigDecimal initialBalance = wallet.balance(); // Should be 0

        // given: 주문 생성 (실제 체크아웃 컨트롤러와 같은 방식)
        String orderId = UUID.randomUUID().toString();
        BigDecimal chargeAmount = new BigDecimal("5000.00");
        
        Order order = new Order();
        order.setUserId(userId);
        order.setRequestId(orderId);
        order.setAmount(chargeAmount);
        order.setDonationId(999L);
        order.setDonationName("Test Donation");
        order.setStatus(OrderStatus.WAIT);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // given: PG 결제 승인 요청 데이터
        ConfirmRequest confirmRequest = new ConfirmRequest(
                "test_payment_key_123",
                orderId,
                chargeAmount.toString()
        );

        // when: 결제 처리 실행
        paymentProcessingService.createPayment(confirmRequest);

        // then: 주문 상태 검증
        Order updatedOrder = orderRepository.findByRequestId(orderId);
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.APPROVED);

        // then: 지갑 잔액 검증 (초기 0 + 충전 5000 = 5000)
        FindWalletResponse updatedWallet = walletService.findWalletByWalletId(wallet.id());
        assertThat(updatedWallet.balance()).isEqualByComparingTo(initialBalance.add(chargeAmount));

        // then: 트랜잭션 기록 검증
        List<Transaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        
        Transaction chargeTransaction = transactions.get(0);
        assertThat(chargeTransaction.getUserId()).isEqualTo(userId);
        assertThat(chargeTransaction.getWalletId()).isEqualTo(wallet.id());
        assertThat(chargeTransaction.getOrderId()).isEqualTo(orderId);
        assertThat(chargeTransaction.getTransactionType()).isEqualTo(TransactionType.CHARGE);
        assertThat(chargeTransaction.getAmount()).isEqualByComparingTo(chargeAmount);
        assertThat(chargeTransaction.getDescription()).isEqualTo("충전");

        // 디버깅 출력
        System.out.printf("✅ PG 결제 통합 테스트 성공:%n");
        System.out.printf("   주문 ID: %s%n", orderId);
        System.out.printf("   주문 상태: %s → %s%n", OrderStatus.WAIT, updatedOrder.getStatus());
        System.out.printf("   지갑 잔액: %s → %s%n", initialBalance, updatedWallet.balance());
        System.out.printf("   트랜잭션: %s %s원 (%s)%n", 
                chargeTransaction.getTransactionType(), 
                chargeTransaction.getAmount(), 
                chargeTransaction.getDescription());
    }

    @Test
    @Transactional
    @DisplayName("PG 결제 멱등성 테스트 - 동일한 주문 ID로 중복 호출해도 안전")
    void pgPayment_idempotency_test() {
        // Mock PG gateway
        doNothing().when(paymentGatewayService).confirm(any(ConfirmRequest.class));

        // given: 사용자와 지갑 생성
        Long userId = 1002L;
        CreatedWalletResponse wallet = walletService.createWallet(new CreateWalletRequest(userId));

        // given: 주문 생성
        String orderId = UUID.randomUUID().toString();
        BigDecimal chargeAmount = new BigDecimal("3000.00");
        
        Order order = new Order();
        order.setUserId(userId);
        order.setRequestId(orderId);
        order.setAmount(chargeAmount);
        order.setStatus(OrderStatus.WAIT);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        ConfirmRequest confirmRequest = new ConfirmRequest(
                "test_payment_key_456",
                orderId,
                chargeAmount.toString()
        );

        // when: 첫 번째 결제 처리
        paymentProcessingService.createPayment(confirmRequest);

        // when: 동일한 요청으로 두 번째 결제 처리 (멱등성 테스트)
        paymentProcessingService.createPayment(confirmRequest);

        // then: 지갑 잔액이 중복 충전되지 않았는지 확인
        FindWalletResponse finalWallet = walletService.findWalletByWalletId(wallet.id());
        assertThat(finalWallet.balance()).isEqualByComparingTo(chargeAmount); // 한 번만 충전됨

        // then: 트랜잭션이 중복 생성되지 않았는지 확인
        List<Transaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1); // 하나의 트랜잭션만 존재

        System.out.printf("✅ PG 결제 멱등성 테스트 성공: 최종 잔액 %s원, 트랜잭션 %d개%n", 
                finalWallet.balance(), transactions.size());
    }
}
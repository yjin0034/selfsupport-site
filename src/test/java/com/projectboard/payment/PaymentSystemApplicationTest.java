package com.projectboard.payment;

import com.projectboard.payment.wallet.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PaymentSystemApplicationTest {
    @Autowired
    WalletRepository walletRepository;

    @Test
    void contextLoads() {
        System.out.println(walletRepository.findAll());
    }

}

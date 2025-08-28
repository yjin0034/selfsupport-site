package com.projectboard.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PaymentSystemApplicationTest {
    @Autowired
    WalletRepository walletRepository;

    @Test
    void contextLoads() {
        System.out.println(walletRepository.findAll());
    }

}

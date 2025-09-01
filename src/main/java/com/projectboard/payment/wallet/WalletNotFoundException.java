package com.projectboard.payment.wallet;

public class WalletNotFoundException extends RuntimeException{
    public WalletNotFoundException(Long key){
        super("지갑이 존재하지 않습니다. key=" + key);
    }
}

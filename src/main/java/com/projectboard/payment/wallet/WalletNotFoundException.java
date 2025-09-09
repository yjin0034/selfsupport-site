package com.projectboard.payment.wallet;

/**
 * 지갑이 존재하지 않을 때 발생하는 예외
 */
public class WalletNotFoundException extends RuntimeException{
    public WalletNotFoundException(Long key){
        super("지갑이 존재하지 않습니다. key=" + key);
    }
}

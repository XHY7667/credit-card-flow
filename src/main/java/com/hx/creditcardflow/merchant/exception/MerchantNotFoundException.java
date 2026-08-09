package com.hx.creditcardflow.merchant.exception;

public class MerchantNotFoundException extends RuntimeException {

    public MerchantNotFoundException(Long id) {
        super("Merchant not found with id: " + id);
    }

    public MerchantNotFoundException(String merchantCode) {
        super("Merchant not found with merchant code: " + merchantCode);
    }
}

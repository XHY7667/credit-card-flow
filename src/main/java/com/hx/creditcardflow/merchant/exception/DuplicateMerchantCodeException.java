package com.hx.creditcardflow.merchant.exception;

public class DuplicateMerchantCodeException extends RuntimeException {

    public DuplicateMerchantCodeException(String merchantCode) {
        super("Merchant code already exists: " + merchantCode);
    }
}

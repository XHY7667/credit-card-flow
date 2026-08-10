package com.hx.creditcardflow.clearing.exception;

public class ClearingCurrencyMismatchException extends RuntimeException {

    public ClearingCurrencyMismatchException(
            String clearingCurrencyCode,
            String authorizationCurrencyCode
    ) {
        super("Clearing currency " + clearingCurrencyCode
                + " must equal authorization currency " + authorizationCurrencyCode);
    }
}

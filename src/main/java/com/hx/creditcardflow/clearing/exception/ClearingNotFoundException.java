package com.hx.creditcardflow.clearing.exception;

public class ClearingNotFoundException extends RuntimeException {

    public ClearingNotFoundException(String clearingReference) {
        super("Clearing not found with clearing reference: " + clearingReference);
    }
}

package com.hx.creditcardflow.reversal.exception;

public class ReversalNotFoundException extends RuntimeException {

    public ReversalNotFoundException(String reversalReference) {
        super("Reversal not found with reversal reference: " + reversalReference);
    }
}

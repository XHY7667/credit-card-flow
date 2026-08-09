package com.hx.creditcardflow.reversal.exception;

public class DuplicateReversalReferenceException extends RuntimeException {

    public DuplicateReversalReferenceException(String reversalReference) {
        super("Reversal reference already exists: " + reversalReference);
    }
}

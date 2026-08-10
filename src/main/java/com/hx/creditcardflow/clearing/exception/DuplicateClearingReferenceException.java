package com.hx.creditcardflow.clearing.exception;

public class DuplicateClearingReferenceException extends RuntimeException {

    public DuplicateClearingReferenceException(String clearingReference) {
        super("Clearing reference already exists: " + clearingReference);
    }
}

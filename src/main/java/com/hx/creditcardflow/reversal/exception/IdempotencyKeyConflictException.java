package com.hx.creditcardflow.reversal.exception;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String idempotencyKey) {
        super("Idempotency key was already used for a different reversal request: " + idempotencyKey);
    }
}

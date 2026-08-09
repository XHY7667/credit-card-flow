package com.hx.creditcardflow.authorization.exception;

public class DuplicateAuthorizationReferenceException extends RuntimeException {

    public DuplicateAuthorizationReferenceException(String authorizationReference) {
        super("Authorization reference already exists: " + authorizationReference);
    }
}

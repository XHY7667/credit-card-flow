package com.hx.creditcardflow.authorization.exception;

public class AuthorizationNotFoundException extends RuntimeException {

    public AuthorizationNotFoundException(String authorizationReference) {
        super("Authorization not found with authorization reference: " + authorizationReference);
    }
}

package com.hx.creditcardflow.clearing.exception;

import com.hx.creditcardflow.authorization.entity.AuthorizationStatus;

public class ClearingNotAllowedException extends RuntimeException {

    public ClearingNotAllowedException(
            String authorizationReference,
            AuthorizationStatus status
    ) {
        super("Authorization cannot be cleared: " + authorizationReference
                + " with status " + status);
    }
}

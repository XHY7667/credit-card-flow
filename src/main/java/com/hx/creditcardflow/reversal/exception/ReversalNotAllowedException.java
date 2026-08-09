package com.hx.creditcardflow.reversal.exception;

import com.hx.creditcardflow.authorization.entity.AuthorizationStatus;

public class ReversalNotAllowedException extends RuntimeException {

    public ReversalNotAllowedException(
            String authorizationReference,
            AuthorizationStatus status
    ) {
        super("Authorization cannot be reversed: " + authorizationReference
                + " with status " + status);
    }
}

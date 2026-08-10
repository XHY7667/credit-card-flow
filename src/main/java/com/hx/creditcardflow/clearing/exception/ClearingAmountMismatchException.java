package com.hx.creditcardflow.clearing.exception;

import java.math.BigDecimal;

public class ClearingAmountMismatchException extends RuntimeException {

    public ClearingAmountMismatchException(
            BigDecimal clearingAmount,
            BigDecimal authorizationAmount
    ) {
        super("Clearing amount " + clearingAmount
                + " must equal authorization amount " + authorizationAmount);
    }
}

package com.hx.creditcardflow.reversal.exception;

import java.math.BigDecimal;

public class ReversalAmountMismatchException extends RuntimeException {

    public ReversalAmountMismatchException(
            BigDecimal reversalAmount,
            BigDecimal authorizationAmount
    ) {
        super("Reversal amount " + reversalAmount
                + " must equal authorization amount " + authorizationAmount);
    }
}

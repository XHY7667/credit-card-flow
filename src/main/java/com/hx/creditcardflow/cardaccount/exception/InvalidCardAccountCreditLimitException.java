package com.hx.creditcardflow.cardaccount.exception;

import java.math.BigDecimal;

public class InvalidCardAccountCreditLimitException extends RuntimeException {

    public InvalidCardAccountCreditLimitException(BigDecimal creditLimit, BigDecimal committedExposure) {
        super("Card account credit limit " + creditLimit
                + " cannot be below committed exposure " + committedExposure);
    }
}

package com.hx.creditcardflow.card.exception;

import java.time.YearMonth;

public class InvalidCardExpirationException extends RuntimeException {

    public InvalidCardExpirationException(YearMonth expiration) {
        super("Card expiration is before the current month: " + expiration);
    }
}

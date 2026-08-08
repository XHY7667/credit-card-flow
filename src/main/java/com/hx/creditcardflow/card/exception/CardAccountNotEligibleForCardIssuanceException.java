package com.hx.creditcardflow.card.exception;

import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;

public class CardAccountNotEligibleForCardIssuanceException extends RuntimeException {

    public CardAccountNotEligibleForCardIssuanceException(
            String accountNumber,
            CardAccountStatus status
    ) {
        super("Card account is not eligible for card issuance: " + accountNumber
                + " with status " + status);
    }
}

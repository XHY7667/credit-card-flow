package com.hx.creditcardflow.card.exception;

public class CardNotFoundException extends RuntimeException {

    public CardNotFoundException(String cardReference) {
        super("Card not found with card reference: " + cardReference);
    }
}

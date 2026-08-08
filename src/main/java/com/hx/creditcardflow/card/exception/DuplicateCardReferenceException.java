package com.hx.creditcardflow.card.exception;

public class DuplicateCardReferenceException extends RuntimeException {

    public DuplicateCardReferenceException(String cardReference) {
        super("Card reference already exists: " + cardReference);
    }
}

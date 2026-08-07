package com.hx.creditcardflow.cardaccount.exception;

public class CardAccountNotFoundException extends RuntimeException {

    public CardAccountNotFoundException(String accountNumber) {
        super("Card account not found with account number: " + accountNumber);
    }
}

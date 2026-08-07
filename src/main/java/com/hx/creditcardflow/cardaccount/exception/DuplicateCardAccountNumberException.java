package com.hx.creditcardflow.cardaccount.exception;

public class DuplicateCardAccountNumberException extends RuntimeException {

    public DuplicateCardAccountNumberException(String accountNumber) {
        super("Card account number already exists: " + accountNumber);
    }
}

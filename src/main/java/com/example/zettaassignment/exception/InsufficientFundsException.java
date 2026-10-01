package com.example.zettaassignment.exception;

import lombok.Getter;

@Getter
public class InsufficientFundsException extends RuntimeException {

    private final String clientId;
    private final String currency;

    public InsufficientFundsException(String clientId, String currency) {
        super("Insufficient funds for client " + clientId + " and currency " + currency);
        this.clientId = clientId;
        this.currency = currency;
    }
}

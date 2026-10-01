package com.example.zettaassignment.exception;

import lombok.Getter;

@Getter
public class BalanceNotFoundException extends RuntimeException {

    private final String clientId;
    private final String currency;

    public BalanceNotFoundException(String clientId, String currency) {
        super("Balance not found for client " + clientId + " and currency " + currency);
        this.clientId = clientId;
        this.currency = currency;
    }
}

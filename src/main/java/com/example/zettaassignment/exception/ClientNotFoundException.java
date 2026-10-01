package com.example.zettaassignment.exception;

import lombok.Getter;

@Getter
public class ClientNotFoundException extends RuntimeException {

    private final String clientId;

    public ClientNotFoundException(String clientId) {
        super("Client not found: " + clientId);
        this.clientId = clientId;
    }
}

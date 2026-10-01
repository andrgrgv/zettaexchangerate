package com.example.zettaassignment.exception;

public class InvalidConversionSearchException extends RuntimeException {

    public InvalidConversionSearchException() {
        super("At least one conversion search filter is required");
    }
}

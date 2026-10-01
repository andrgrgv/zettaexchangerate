package com.example.zettaassignment.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        String code,
        String message,
        Instant timestamp,
        Map<String, String> details
) {

    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now(), Map.of());
    }

    public ErrorResponse(String code, String message, Map<String, String> details) {
        this(code, message, Instant.now(), details);
    }
}

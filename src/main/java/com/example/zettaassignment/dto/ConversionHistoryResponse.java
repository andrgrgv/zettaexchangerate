package com.example.zettaassignment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

public record ConversionHistoryResponse(
        UUID transactionId,
        String clientId,
        BigDecimal sourceAmount,
        Currency sourceCurrency,
        BigDecimal targetAmount,
        Currency targetCurrency,
        BigDecimal rate,
        Instant timestamp
) {}

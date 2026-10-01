package com.example.zettaassignment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

public record ConversionResponse(
        UUID transactionId,
        BigDecimal sourceAmount,
        Currency sourceCurrency,
        BigDecimal targetAmount,
        Currency targetCurrency,
        BigDecimal rate,
        Instant timestamp,
        List<BalanceResponse> balances
) {}

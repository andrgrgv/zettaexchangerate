package com.example.zettaassignment.dto;

import java.math.BigDecimal;
import java.util.Currency;

public record BalanceResponse(
        Currency currency,
        BigDecimal amount
) {}

package com.example.zettaassignment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Currency;

public record ConversionRequest(

        @NotNull
        Currency sourceCurrency,

        @NotNull
        Currency targetCurrency,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 15, fraction = 4)
        BigDecimal amount

) {}

package com.example.zettaassignment.provider;

import com.example.zettaassignment.dto.RateResponse;

import java.util.Currency;

public interface RateProvider {

    RateResponse getRate(
            Currency from,
            Currency to
    );
}

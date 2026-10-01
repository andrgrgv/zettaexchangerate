package com.example.zettaassignment.service;

import com.example.zettaassignment.dto.RateResponse;
import com.example.zettaassignment.provider.RateProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

@Service
@RequiredArgsConstructor
public class RateService {

    private final RateProvider rateProvider;

    @Cacheable(cacheNames = "rates", key = "#from.currencyCode + ':' + #to.currencyCode")
    public RateResponse getRate(Currency from, Currency to) {
        if (from.equals(to)) {
            return new RateResponse(
                    from.getCurrencyCode(),
                    to.getCurrencyCode(),
                    BigDecimal.ONE,
                    Instant.now()
            );
        }

        return rateProvider.getRate(from, to);
    }
}

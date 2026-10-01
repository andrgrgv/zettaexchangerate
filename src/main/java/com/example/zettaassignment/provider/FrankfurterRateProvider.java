package com.example.zettaassignment.provider;

import com.example.zettaassignment.dto.RateResponse;
import com.example.zettaassignment.exception.RateProviderException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FrankfurterRateProvider
        implements RateProvider {

    private final RestClient frankfurterRestClient;

    @Override
    public RateResponse getRate(Currency from, Currency to) {
        if (from.equals(to)) {
            return new RateResponse(
                    from.getCurrencyCode(),
                    to.getCurrencyCode(),
                    BigDecimal.ONE,
                    Instant.now()
            );
        }

        try {
            Map<String, Object> response = frankfurterRestClient.get()
                    .uri(
                            "/v2/rate/{from}/{to}",
                            from.getCurrencyCode(),
                            to.getCurrencyCode()
                    )
                    .retrieve()
                    .body(Map.class);

            Object rateValue = response == null ? null : response.get("rate");
            if (!(rateValue instanceof Number)) {
                throw new RateProviderException(
                        "Frankfurter returned an invalid response"
                );
            }

            BigDecimal rate = new BigDecimal(rateValue.toString());

            return new RateResponse(
                    from.getCurrencyCode(),
                    to.getCurrencyCode(),
                    rate,
                    Instant.now()
            );
        } catch (RestClientException ex) {
            throw new RateProviderException(
                    "Failed to fetch exchange rate from Frankfurter",
                    ex
            );
        }
    }
}

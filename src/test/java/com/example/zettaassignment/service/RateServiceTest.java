package com.example.zettaassignment.service;

import com.example.zettaassignment.dto.RateResponse;
import com.example.zettaassignment.provider.RateProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateServiceTest {

    @Mock
    private RateProvider rateProvider;

    @InjectMocks
    private RateService rateService;

    @Test
    void getRateReturnsOneWithoutProviderWhenCurrenciesMatch() {
        Currency usd = Currency.getInstance("USD");

        RateResponse response = rateService.getRate(usd, usd);

        assertThat(response.from()).isEqualTo("USD");
        assertThat(response.to()).isEqualTo("USD");
        assertThat(response.rate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(response.timestamp()).isNotNull();
        verifyNoInteractions(rateProvider);
    }

    @Test
    void getRateDelegatesToProviderWhenCurrenciesDiffer() {
        Currency usd = Currency.getInstance("USD");
        Currency eur = Currency.getInstance("EUR");
        RateResponse providerResponse = new RateResponse(
                "USD",
                "EUR",
                new BigDecimal("0.92000000"),
                Instant.parse("2026-10-01T10:00:00Z")
        );
        when(rateProvider.getRate(usd, eur)).thenReturn(providerResponse);

        RateResponse response = rateService.getRate(usd, eur);

        assertThat(response).isSameAs(providerResponse);
        verify(rateProvider).getRate(usd, eur);
    }
}

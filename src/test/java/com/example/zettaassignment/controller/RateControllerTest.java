package com.example.zettaassignment.controller;

import com.example.zettaassignment.dto.RateResponse;
import com.example.zettaassignment.exception.RateProviderException;
import com.example.zettaassignment.service.RateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RateController.class)
class RateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateService rateService;

    @Test
    void getRateReturnsRate() throws Exception {
        when(rateService.getRate(Currency.getInstance("USD"), Currency.getInstance("EUR")))
                .thenReturn(new RateResponse(
                        "USD",
                        "EUR",
                        new BigDecimal("0.92000000"),
                        Instant.parse("2026-10-01T10:00:00Z")
                ));

        mockMvc.perform(get("/rates")
                        .param("from", "USD")
                        .param("to", "EUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("USD"))
                .andExpect(jsonPath("$.to").value("EUR"))
                .andExpect(jsonPath("$.rate").value(0.92000000));
    }

    @Test
    void getRateReturnsProviderFailureError() throws Exception {
        when(rateService.getRate(Currency.getInstance("USD"), Currency.getInstance("EUR")))
                .thenThrow(new RateProviderException("Failed to fetch exchange rate from Frankfurter"));

        mockMvc.perform(get("/rates")
                        .param("from", "USD")
                        .param("to", "EUR"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("RATE_PROVIDER_FAILURE"))
                .andExpect(jsonPath("$.message").value("Failed to fetch exchange rate from Frankfurter"));
    }

    @Test
    void getRateReturnsBadRequestWhenRequiredParameterIsMissing() throws Exception {
        mockMvc.perform(get("/rates")
                        .param("to", "EUR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}

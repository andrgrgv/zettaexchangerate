package com.example.zettaassignment.controller;

import com.example.zettaassignment.dto.BalanceResponse;
import com.example.zettaassignment.dto.ConversionHistoryResponse;
import com.example.zettaassignment.dto.ConversionRequest;
import com.example.zettaassignment.dto.ConversionResponse;
import com.example.zettaassignment.exception.InsufficientFundsException;
import com.example.zettaassignment.service.ConversionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConversionController.class)
class ConversionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ConversionService conversionService;

    @Test
    void convertReturnsConversion() throws Exception {
        UUID transactionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ConversionRequest request = new ConversionRequest(
                Currency.getInstance("USD"),
                Currency.getInstance("EUR"),
                new BigDecimal("100.0000")
        );
        when(conversionService.convert(eq("CLIENT-001"), eq("key-001"), any(ConversionRequest.class)))
                .thenReturn(new ConversionResponse(
                        transactionId,
                        new BigDecimal("100.0000"),
                        Currency.getInstance("USD"),
                        new BigDecimal("92.0000"),
                        Currency.getInstance("EUR"),
                        new BigDecimal("0.92000000"),
                        Instant.parse("2026-10-01T10:00:00Z"),
                        List.of(
                                new BalanceResponse(Currency.getInstance("USD"), new BigDecimal("900.0000")),
                                new BalanceResponse(Currency.getInstance("EUR"), new BigDecimal("592.0000"))
                        )
                ));

        mockMvc.perform(post("/conversions")
                        .header("X-Client-Id", "CLIENT-001")
                        .header("Idempotency-Key", "key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.sourceCurrency").value("USD"))
                .andExpect(jsonPath("$.targetCurrency").value("EUR"))
                .andExpect(jsonPath("$.sourceAmount").value(100.0000))
                .andExpect(jsonPath("$.targetAmount").value(92.0000))
                .andExpect(jsonPath("$.rate").value(0.92000000));

        ArgumentCaptor<ConversionRequest> requestCaptor = ArgumentCaptor.forClass(ConversionRequest.class);
        verify(conversionService).convert(eq("CLIENT-001"), eq("key-001"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().sourceCurrency()).isEqualTo(Currency.getInstance("USD"));
        assertThat(requestCaptor.getValue().targetCurrency()).isEqualTo(Currency.getInstance("EUR"));
        assertThat(requestCaptor.getValue().amount()).isEqualByComparingTo("100.0000");
    }

    @Test
    void convertRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/conversions")
                        .header("X-Client-Id", "CLIENT-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceCurrency":"USD","targetCurrency":"EUR","amount":100}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void convertReturnsInsufficientFundsError() throws Exception {
        when(conversionService.convert(eq("CLIENT-001"), eq("too-much"), any(ConversionRequest.class)))
                .thenThrow(new InsufficientFundsException("CLIENT-001", "USD"));

        mockMvc.perform(post("/conversions")
                        .header("X-Client-Id", "CLIENT-001")
                        .header("Idempotency-Key", "too-much")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceCurrency":"USD","targetCurrency":"EUR","amount":999999}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void getConversionsSearchesHistory() throws Exception {
        UUID transactionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        LocalDate date = LocalDate.of(2026, 10, 1);
        when(conversionService.findConversions(eq(transactionId), eq(date), eq("CLIENT-001"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new ConversionHistoryResponse(
                        transactionId,
                        "CLIENT-001",
                        new BigDecimal("100.0000"),
                        Currency.getInstance("USD"),
                        new BigDecimal("92.0000"),
                        Currency.getInstance("EUR"),
                        new BigDecimal("0.92000000"),
                        Instant.parse("2026-10-01T10:00:00Z")
                ))));

        mockMvc.perform(get("/conversions")
                        .param("transactionId", transactionId.toString())
                        .param("date", "2026-10-01")
                        .param("clientId", "CLIENT-001")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.content[0].clientId").value("CLIENT-001"))
                .andExpect(jsonPath("$.content[0].sourceCurrency").value("USD"))
                .andExpect(jsonPath("$.content[0].balances").doesNotExist());
    }
}

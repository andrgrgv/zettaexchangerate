package com.example.zettaassignment.controller;

import com.example.zettaassignment.dto.BalanceResponse;
import com.example.zettaassignment.exception.ClientNotFoundException;
import com.example.zettaassignment.service.BalanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientController.class)
class ClientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BalanceService balanceService;

    @Test
    void getBalancesReturnsClientBalances() throws Exception {
        when(balanceService.getBalances("CLIENT-001")).thenReturn(List.of(
                new BalanceResponse(Currency.getInstance("EUR"), new BigDecimal("8000.0000")),
                new BalanceResponse(Currency.getInstance("USD"), new BigDecimal("10000.0000"))
        ));

        mockMvc.perform(get("/clients/CLIENT-001/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].amount").value(8000.0000))
                .andExpect(jsonPath("$[1].currency").value("USD"))
                .andExpect(jsonPath("$[1].amount").value(10000.0000));
    }

    @Test
    void getBalancesReturnsNotFoundForUnknownClient() throws Exception {
        when(balanceService.getBalances("UNKNOWN"))
                .thenThrow(new ClientNotFoundException("UNKNOWN"));

        mockMvc.perform(get("/clients/UNKNOWN/balances"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Client not found: UNKNOWN"));
    }
}

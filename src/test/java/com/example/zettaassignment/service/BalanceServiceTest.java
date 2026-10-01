package com.example.zettaassignment.service;

import com.example.zettaassignment.domain.Balance;
import com.example.zettaassignment.domain.BalanceId;
import com.example.zettaassignment.domain.Client;
import com.example.zettaassignment.dto.BalanceResponse;
import com.example.zettaassignment.exception.BalanceNotFoundException;
import com.example.zettaassignment.exception.ClientNotFoundException;
import com.example.zettaassignment.repository.BalanceRepository;
import com.example.zettaassignment.repository.ClientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    private static final String CLIENT_ID = "CLIENT-001";

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private BalanceRepository balanceRepository;

    @InjectMocks
    private BalanceService balanceService;

    @Test
    void getBalancesReturnsClientBalances() {
        Client client = new Client(CLIENT_ID);
        Balance usdBalance = new Balance(client, "USD", new BigDecimal("100.0000"));
        Balance eurBalance = new Balance(client, "EUR", new BigDecimal("50.2500"));

        when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
        when(balanceRepository.findByIdClientIdOrderByIdCurrency(CLIENT_ID))
                .thenReturn(List.of(eurBalance, usdBalance));

        List<BalanceResponse> balances = balanceService.getBalances(CLIENT_ID);

        assertThat(balances).containsExactly(
                new BalanceResponse(Currency.getInstance("EUR"), new BigDecimal("50.2500")),
                new BalanceResponse(Currency.getInstance("USD"), new BigDecimal("100.0000"))
        );
    }

    @Test
    void getBalancesThrowsWhenClientDoesNotExist() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> balanceService.getBalances(CLIENT_ID))
                .isInstanceOf(ClientNotFoundException.class)
                .hasMessage("Client not found: " + CLIENT_ID);

        verifyNoInteractions(balanceRepository);
    }

    @Test
    void getBalanceForUpdateReturnsLockedBalance() {
        BalanceId balanceId = new BalanceId(CLIENT_ID, "USD");
        Balance balance = new Balance(new Client(CLIENT_ID), "USD", new BigDecimal("100.0000"));
        when(balanceRepository.findByIdForUpdate(balanceId)).thenReturn(Optional.of(balance));

        Balance result = balanceService.getBalanceForUpdate(CLIENT_ID, "USD");

        assertThat(result).isSameAs(balance);
        verify(balanceRepository).findByIdForUpdate(balanceId);
    }

    @Test
    void getBalanceForUpdateThrowsWhenBalanceDoesNotExist() {
        BalanceId balanceId = new BalanceId(CLIENT_ID, "USD");
        when(balanceRepository.findByIdForUpdate(balanceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.getBalanceForUpdate(CLIENT_ID, "USD"))
                .isInstanceOf(BalanceNotFoundException.class)
                .hasMessage("Balance not found for client CLIENT-001 and currency USD");
    }
}

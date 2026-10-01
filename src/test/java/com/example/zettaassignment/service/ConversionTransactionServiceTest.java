package com.example.zettaassignment.service;

import com.example.zettaassignment.domain.Balance;
import com.example.zettaassignment.domain.Client;
import com.example.zettaassignment.domain.Conversion;
import com.example.zettaassignment.dto.ConversionResponse;
import com.example.zettaassignment.exception.InsufficientFundsException;
import com.example.zettaassignment.repository.ClientRepository;
import com.example.zettaassignment.repository.ConversionRepository;
import com.example.zettaassignment.repository.IdempotencyLockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversionTransactionServiceTest {

    private static final String CLIENT_ID = "CLIENT-001";
    private static final String IDEMPOTENCY_KEY = "conversion-001";

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ConversionRepository conversionRepository;

    @Mock
    private IdempotencyLockRepository idempotencyLockRepository;

    @Mock
    private BalanceService balanceService;

    @Mock
    private ConversionResponseMapper responseMapper;

    @InjectMocks
    private ConversionTransactionService conversionTransactionService;

    @Test
    void applyConversionLocksBalancesDebitsCreditsAndSavesConversion() {
        Client client = new Client(CLIENT_ID);
        Balance sourceBalance = new Balance(client, "USD", new BigDecimal("1000.0000"));
        Balance targetBalance = new Balance(client, "EUR", new BigDecimal("500.0000"));
        ConversionResponse expectedResponse = response();

        when(balanceService.getBalanceForUpdate(CLIENT_ID, "EUR")).thenReturn(targetBalance);
        when(balanceService.getBalanceForUpdate(CLIENT_ID, "USD")).thenReturn(sourceBalance);
        when(conversionRepository.findByClient_IdAndIdempotencyKey(CLIENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(clientRepository.getReferenceById(CLIENT_ID)).thenReturn(client);
        when(conversionRepository.save(any(Conversion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(responseMapper.toResponse(any(Conversion.class))).thenReturn(expectedResponse);

        ConversionResponse response = conversionTransactionService.applyConversion(
                CLIENT_ID,
                IDEMPOTENCY_KEY,
                "USD",
                new BigDecimal("100.0000"),
                "EUR",
                new BigDecimal("92.0000"),
                new BigDecimal("0.92000000")
        );

        assertThat(response).isSameAs(expectedResponse);
        assertThat(sourceBalance.getAmount()).isEqualByComparingTo("900.0000");
        assertThat(targetBalance.getAmount()).isEqualByComparingTo("592.0000");

        ArgumentCaptor<Conversion> conversionCaptor = ArgumentCaptor.forClass(Conversion.class);
        verify(conversionRepository).save(conversionCaptor.capture());
        Conversion savedConversion = conversionCaptor.getValue();
        assertThat(savedConversion.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(savedConversion.getSourceCurrency()).isEqualTo("USD");
        assertThat(savedConversion.getSourceAmount()).isEqualByComparingTo("100.0000");
        assertThat(savedConversion.getTargetCurrency()).isEqualTo("EUR");
        assertThat(savedConversion.getTargetAmount()).isEqualByComparingTo("92.0000");
        assertThat(savedConversion.getRate()).isEqualByComparingTo("0.92000000");
        assertThat(savedConversion.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);

        InOrder lockOrder = inOrder(idempotencyLockRepository, balanceService);
        lockOrder.verify(idempotencyLockRepository).lock(CLIENT_ID, IDEMPOTENCY_KEY);
        lockOrder.verify(balanceService).getBalanceForUpdate(CLIENT_ID, "EUR");
        lockOrder.verify(balanceService).getBalanceForUpdate(CLIENT_ID, "USD");
    }

    @Test
    void applyConversionReturnsExistingConversionAfterLocksForIdempotencyReplay() {
        Client client = new Client(CLIENT_ID);
        Balance sourceBalance = new Balance(client, "USD", new BigDecimal("1000.0000"));
        Balance targetBalance = new Balance(client, "EUR", new BigDecimal("500.0000"));
        Conversion existingConversion = new Conversion(
                client,
                "USD",
                new BigDecimal("100.0000"),
                "EUR",
                new BigDecimal("92.0000"),
                new BigDecimal("0.92000000"),
                IDEMPOTENCY_KEY
        );
        ConversionResponse expectedResponse = response();

        when(balanceService.getBalanceForUpdate(CLIENT_ID, "EUR")).thenReturn(targetBalance);
        when(balanceService.getBalanceForUpdate(CLIENT_ID, "USD")).thenReturn(sourceBalance);
        when(conversionRepository.findByClient_IdAndIdempotencyKey(CLIENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existingConversion));
        when(responseMapper.toResponse(existingConversion)).thenReturn(expectedResponse);

        ConversionResponse response = conversionTransactionService.applyConversion(
                CLIENT_ID,
                IDEMPOTENCY_KEY,
                "USD",
                new BigDecimal("100.0000"),
                "EUR",
                new BigDecimal("92.0000"),
                new BigDecimal("0.92000000")
        );

        assertThat(response).isSameAs(expectedResponse);
        assertThat(sourceBalance.getAmount()).isEqualByComparingTo("1000.0000");
        assertThat(targetBalance.getAmount()).isEqualByComparingTo("500.0000");
        verify(idempotencyLockRepository).lock(CLIENT_ID, IDEMPOTENCY_KEY);
        verifyNoInteractions(clientRepository);
        verify(conversionRepository, never()).save(any());
    }

    @Test
    void applyConversionThrowsWhenSourceBalanceHasInsufficientFunds() {
        Client client = new Client(CLIENT_ID);
        Balance sourceBalance = new Balance(client, "USD", new BigDecimal("25.0000"));
        Balance targetBalance = new Balance(client, "EUR", new BigDecimal("500.0000"));

        when(balanceService.getBalanceForUpdate(CLIENT_ID, "EUR")).thenReturn(targetBalance);
        when(balanceService.getBalanceForUpdate(CLIENT_ID, "USD")).thenReturn(sourceBalance);
        when(conversionRepository.findByClient_IdAndIdempotencyKey(CLIENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversionTransactionService.applyConversion(
                CLIENT_ID,
                IDEMPOTENCY_KEY,
                "USD",
                new BigDecimal("100.0000"),
                "EUR",
                new BigDecimal("92.0000"),
                new BigDecimal("0.92000000")
        ))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Insufficient funds for client CLIENT-001 and currency USD");

        assertThat(sourceBalance.getAmount()).isEqualByComparingTo("25.0000");
        assertThat(targetBalance.getAmount()).isEqualByComparingTo("500.0000");
        verify(idempotencyLockRepository).lock(CLIENT_ID, IDEMPOTENCY_KEY);
        verifyNoInteractions(clientRepository, responseMapper);
        verify(conversionRepository, never()).save(any());
    }

    private ConversionResponse response() {
        return new ConversionResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                new BigDecimal("100.0000"),
                Currency.getInstance("USD"),
                new BigDecimal("92.0000"),
                Currency.getInstance("EUR"),
                new BigDecimal("0.92000000"),
                Instant.parse("2026-10-01T10:00:00Z"),
                List.of()
        );
    }
}

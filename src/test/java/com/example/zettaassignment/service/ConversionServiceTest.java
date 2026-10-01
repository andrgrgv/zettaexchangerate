package com.example.zettaassignment.service;

import com.example.zettaassignment.domain.Client;
import com.example.zettaassignment.domain.Conversion;
import com.example.zettaassignment.dto.ConversionHistoryResponse;
import com.example.zettaassignment.dto.ConversionRequest;
import com.example.zettaassignment.dto.ConversionResponse;
import com.example.zettaassignment.dto.RateResponse;
import com.example.zettaassignment.exception.ClientNotFoundException;
import com.example.zettaassignment.exception.InvalidConversionSearchException;
import com.example.zettaassignment.repository.ClientRepository;
import com.example.zettaassignment.repository.ConversionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
class ConversionServiceTest {

    private static final String CLIENT_ID = "CLIENT-001";
    private static final String IDEMPOTENCY_KEY = "conversion-001";

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ConversionRepository conversionRepository;

    @Mock
    private RateService rateService;

    @Mock
    private ConversionTransactionService conversionTransactionService;

    @Mock
    private ConversionResponseMapper responseMapper;

    @InjectMocks
    private ConversionService conversionService;

    @Test
    void convertFetchesRateBeforeDelegatingToTransactionalUpdate() {
        ConversionRequest request = new ConversionRequest(
                Currency.getInstance("USD"),
                Currency.getInstance("EUR"),
                new BigDecimal("100")
        );
        ConversionResponse expectedResponse = response();

        when(conversionRepository.findByClient_IdAndIdempotencyKey(CLIENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
        when(rateService.getRate(request.sourceCurrency(), request.targetCurrency()))
                .thenReturn(new RateResponse("USD", "EUR", new BigDecimal("0.920000004"), Instant.now()));
        when(conversionTransactionService.applyConversion(
                CLIENT_ID,
                IDEMPOTENCY_KEY,
                "USD",
                new BigDecimal("100.0000"),
                "EUR",
                new BigDecimal("92.0000"),
                new BigDecimal("0.92000000")
        )).thenReturn(expectedResponse);

        ConversionResponse response = conversionService.convert(CLIENT_ID, IDEMPOTENCY_KEY, request);

        assertThat(response).isSameAs(expectedResponse);

        InOrder conversionOrder = inOrder(rateService, conversionTransactionService);
        conversionOrder.verify(rateService).getRate(request.sourceCurrency(), request.targetCurrency());
        conversionOrder.verify(conversionTransactionService).applyConversion(
                CLIENT_ID,
                IDEMPOTENCY_KEY,
                "USD",
                new BigDecimal("100.0000"),
                "EUR",
                new BigDecimal("92.0000"),
                new BigDecimal("0.92000000")
        );
    }

    @Test
    void convertReturnsExistingConversionForIdempotencyReplay() {
        Client client = new Client(CLIENT_ID);
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

        when(conversionRepository.findByClient_IdAndIdempotencyKey(CLIENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existingConversion));
        when(responseMapper.toResponse(existingConversion)).thenReturn(expectedResponse);

        ConversionResponse response = conversionService.convert(
                CLIENT_ID,
                IDEMPOTENCY_KEY,
                new ConversionRequest(
                        Currency.getInstance("USD"),
                        Currency.getInstance("EUR"),
                        new BigDecimal("100")
                )
        );

        assertThat(response).isSameAs(expectedResponse);
        verifyNoInteractions(clientRepository, rateService, conversionTransactionService);
    }

    @Test
    void convertThrowsBeforeRateLookupWhenClientDoesNotExist() {
        ConversionRequest request = new ConversionRequest(
                Currency.getInstance("USD"),
                Currency.getInstance("EUR"),
                new BigDecimal("100")
        );

        when(conversionRepository.findByClient_IdAndIdempotencyKey(CLIENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> conversionService.convert(CLIENT_ID, IDEMPOTENCY_KEY, request))
                .isInstanceOf(ClientNotFoundException.class)
                .hasMessage("Client not found: CLIENT-001");

        verifyNoInteractions(rateService, conversionTransactionService);
    }

    @Test
    void findConversionsSearchesWithDateRangeAndPageable() {
        Client client = new Client(CLIENT_ID);
        UUID transactionId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        LocalDate date = LocalDate.of(2026, 10, 1);
        Pageable pageable = PageRequest.of(1, 2);
        Conversion conversion = new Conversion(
                client,
                "USD",
                new BigDecimal("100.0000"),
                "EUR",
                new BigDecimal("92.0000"),
                new BigDecimal("0.92000000"),
                IDEMPOTENCY_KEY
        );
        ConversionHistoryResponse mappedResponse = historyResponse();

        when(conversionRepository.findByIdAndClient_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                transactionId,
                CLIENT_ID,
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-02T00:00:00Z"),
                pageable
        )).thenReturn(new PageImpl<>(List.of(conversion), pageable, 3));
        when(responseMapper.toHistoryResponse(conversion)).thenReturn(mappedResponse);

        Page<ConversionHistoryResponse> result = conversionService.findConversions(
                transactionId,
                date,
                CLIENT_ID,
                pageable
        );

        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).containsExactly(mappedResponse);

        verify(conversionRepository).findByIdAndClient_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                transactionId,
                CLIENT_ID,
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-02T00:00:00Z"),
                pageable
        );
        verify(responseMapper, never()).toResponse(any());
    }

    @Test
    void findConversionsThrowsWhenNoFiltersProvided() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> conversionService.findConversions(null, null, " ", pageable))
                .isInstanceOf(InvalidConversionSearchException.class)
                .hasMessage("At least one conversion search filter is required");

        verify(conversionRepository, never()).findByIdAndClient_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(),
                any(),
                any(),
                any(),
                any()
        );
        verify(conversionRepository, never()).findByClient_Id(any(), any());
    }

    @Test
    void findConversionsWithOnlyClientIdUsesClientSearch() {
        Client client = new Client(CLIENT_ID);
        Pageable pageable = PageRequest.of(0, 20);
        Conversion conversion = new Conversion(
                client,
                "USD",
                new BigDecimal("100.0000"),
                "EUR",
                new BigDecimal("92.0000"),
                new BigDecimal("0.92000000"),
                IDEMPOTENCY_KEY
        );
        ConversionHistoryResponse mappedResponse = historyResponse();

        when(conversionRepository.findByClient_Id(CLIENT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(conversion), pageable, 1));
        when(responseMapper.toHistoryResponse(conversion)).thenReturn(mappedResponse);

        Page<ConversionHistoryResponse> result = conversionService.findConversions(
                null,
                null,
                CLIENT_ID,
                pageable
        );

        assertThat(result.getContent()).containsExactly(mappedResponse);
        verify(conversionRepository).findByClient_Id(CLIENT_ID, pageable);
    }

    @Test
    void findConversionsWithOnlyTransactionIdUsesTransactionSearch() {
        UUID transactionId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Pageable pageable = PageRequest.of(0, 20);
        Conversion conversion = conversion();
        ConversionHistoryResponse mappedResponse = historyResponse();

        when(conversionRepository.findByIdIs(transactionId, pageable))
                .thenReturn(new PageImpl<>(List.of(conversion), pageable, 1));
        when(responseMapper.toHistoryResponse(conversion)).thenReturn(mappedResponse);

        Page<ConversionHistoryResponse> result = conversionService.findConversions(
                transactionId,
                null,
                null,
                pageable
        );

        assertThat(result.getContent()).containsExactly(mappedResponse);
        verify(conversionRepository).findByIdIs(transactionId, pageable);
    }

    @Test
    void findConversionsWithOnlyDateUsesDateSearch() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        Pageable pageable = PageRequest.of(0, 20);
        Conversion conversion = conversion();
        ConversionHistoryResponse mappedResponse = historyResponse();

        when(conversionRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-02T00:00:00Z"),
                pageable
        )).thenReturn(new PageImpl<>(List.of(conversion), pageable, 1));
        when(responseMapper.toHistoryResponse(conversion)).thenReturn(mappedResponse);

        Page<ConversionHistoryResponse> result = conversionService.findConversions(
                null,
                date,
                null,
                pageable
        );

        assertThat(result.getContent()).containsExactly(mappedResponse);
        verify(conversionRepository).findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-02T00:00:00Z"),
                pageable
        );
    }

    @Test
    void findConversionsWithTransactionIdAndClientIdUsesTransactionClientSearch() {
        UUID transactionId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Pageable pageable = PageRequest.of(0, 20);
        Conversion conversion = conversion();
        ConversionHistoryResponse mappedResponse = historyResponse();

        when(conversionRepository.findByIdAndClient_Id(transactionId, CLIENT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(conversion), pageable, 1));
        when(responseMapper.toHistoryResponse(conversion)).thenReturn(mappedResponse);

        Page<ConversionHistoryResponse> result = conversionService.findConversions(
                transactionId,
                null,
                CLIENT_ID,
                pageable
        );

        assertThat(result.getContent()).containsExactly(mappedResponse);
        verify(conversionRepository).findByIdAndClient_Id(transactionId, CLIENT_ID, pageable);
    }

    @Test
    void findConversionsWithTransactionIdAndDateUsesTransactionDateSearch() {
        UUID transactionId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        LocalDate date = LocalDate.of(2026, 10, 1);
        Pageable pageable = PageRequest.of(0, 20);
        Conversion conversion = conversion();
        ConversionHistoryResponse mappedResponse = historyResponse();

        when(conversionRepository.findByIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                transactionId,
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-02T00:00:00Z"),
                pageable
        )).thenReturn(new PageImpl<>(List.of(conversion), pageable, 1));
        when(responseMapper.toHistoryResponse(conversion)).thenReturn(mappedResponse);

        Page<ConversionHistoryResponse> result = conversionService.findConversions(
                transactionId,
                date,
                null,
                pageable
        );

        assertThat(result.getContent()).containsExactly(mappedResponse);
        verify(conversionRepository).findByIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                transactionId,
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-02T00:00:00Z"),
                pageable
        );
    }

    @Test
    void findConversionsWithClientIdAndDateUsesClientDateSearch() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        Pageable pageable = PageRequest.of(0, 20);
        Conversion conversion = conversion();
        ConversionHistoryResponse mappedResponse = historyResponse();

        when(conversionRepository.findByClient_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                CLIENT_ID,
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-02T00:00:00Z"),
                pageable
        )).thenReturn(new PageImpl<>(List.of(conversion), pageable, 1));
        when(responseMapper.toHistoryResponse(conversion)).thenReturn(mappedResponse);

        Page<ConversionHistoryResponse> result = conversionService.findConversions(
                null,
                date,
                CLIENT_ID,
                pageable
        );

        assertThat(result.getContent()).containsExactly(mappedResponse);
        verify(conversionRepository).findByClient_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                CLIENT_ID,
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-02T00:00:00Z"),
                pageable
        );
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

    private ConversionHistoryResponse historyResponse() {
        return new ConversionHistoryResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                CLIENT_ID,
                new BigDecimal("100.0000"),
                Currency.getInstance("USD"),
                new BigDecimal("92.0000"),
                Currency.getInstance("EUR"),
                new BigDecimal("0.92000000"),
                Instant.parse("2026-10-01T10:00:00Z")
        );
    }

    private Conversion conversion() {
        return new Conversion(
                new Client(CLIENT_ID),
                "USD",
                new BigDecimal("100.0000"),
                "EUR",
                new BigDecimal("92.0000"),
                new BigDecimal("0.92000000"),
                IDEMPOTENCY_KEY
        );
    }
}

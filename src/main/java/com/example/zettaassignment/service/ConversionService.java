package com.example.zettaassignment.service;

import com.example.zettaassignment.dto.ConversionRequest;
import com.example.zettaassignment.dto.ConversionResponse;
import com.example.zettaassignment.dto.ConversionHistoryResponse;
import com.example.zettaassignment.dto.RateResponse;
import com.example.zettaassignment.domain.Conversion;
import com.example.zettaassignment.exception.ClientNotFoundException;
import com.example.zettaassignment.exception.InvalidIdempotencyKeyException;
import com.example.zettaassignment.exception.InvalidConversionSearchException;
import com.example.zettaassignment.repository.ClientRepository;
import com.example.zettaassignment.repository.ConversionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversionService {

    private static final int MONEY_SCALE = 4;
    private static final int RATE_SCALE = 8;
    private static final int TRANSACTION_ID_FILTER = 4;
    private static final int CLIENT_ID_FILTER = 2;
    private static final int DATE_FILTER = 1;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private final ClientRepository clientRepository;
    private final ConversionRepository conversionRepository;
    private final RateService rateService;
    private final ConversionTransactionService conversionTransactionService;
    private final ConversionResponseMapper responseMapper;

    public ConversionResponse convert(
            String clientId,
            String idempotencyKey,
            ConversionRequest request
    ) {
        String replayKey = normalizeIdempotencyKey(idempotencyKey);
        Conversion existingConversion = findExistingConversion(clientId, replayKey);
        if (existingConversion != null) {
            return responseMapper.toResponse(existingConversion);
        }

        requireClient(clientId);

        String sourceCurrency = request.sourceCurrency().getCurrencyCode();
        String targetCurrency = request.targetCurrency().getCurrencyCode();
        BigDecimal sourceAmount = scaleMoney(request.amount());

        RateResponse rateResponse = rateService.getRate(
                request.sourceCurrency(),
                request.targetCurrency()
        );
        BigDecimal rate = rateResponse.rate().setScale(RATE_SCALE, ROUNDING_MODE);
        BigDecimal targetAmount = scaleMoney(sourceAmount.multiply(rate));

        return conversionTransactionService.applyConversion(
                clientId,
                replayKey,
                sourceCurrency,
                sourceAmount,
                targetCurrency,
                targetAmount,
                rate
        );
    }

    @Transactional(readOnly = true)
    public Page<ConversionHistoryResponse> findConversions(
            UUID transactionId,
            LocalDate date,
            String clientId,
            Pageable pageable
    ) {
        if (transactionId == null && date == null && !StringUtils.hasText(clientId)) {
            throw new InvalidConversionSearchException();
        }

        Instant fromInclusive = date == null ? null : date.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toExclusive = date == null ? null : date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        String normalizedClientId = StringUtils.hasText(clientId) ? clientId : null;

        return searchConversions(
                transactionId,
                normalizedClientId,
                fromInclusive,
                toExclusive,
                pageable
        ).map(responseMapper::toHistoryResponse);
    }

    private Conversion findExistingConversion(String clientId, String idempotencyKey) {
        return conversionRepository.findByClient_IdAndIdempotencyKey(clientId, idempotencyKey)
                .orElse(null);
    }

    private Page<Conversion> searchConversions(
            UUID transactionId,
            String clientId,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable
    ) {
        boolean hasTransactionId = transactionId != null;
        boolean hasClientId = StringUtils.hasText(clientId);
        boolean hasDate = fromInclusive != null && toExclusive != null;
        int filters = (hasTransactionId ? TRANSACTION_ID_FILTER : 0)
                + (hasClientId ? CLIENT_ID_FILTER : 0)
                + (hasDate ? DATE_FILTER : 0);

        return switch (filters) {
            case TRANSACTION_ID_FILTER + CLIENT_ID_FILTER + DATE_FILTER ->
                    conversionRepository.findByIdAndClient_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                            transactionId,
                            clientId,
                            fromInclusive,
                            toExclusive,
                            pageable
                    );
            case TRANSACTION_ID_FILTER + CLIENT_ID_FILTER ->
                    conversionRepository.findByIdAndClient_Id(transactionId, clientId, pageable);
            case TRANSACTION_ID_FILTER + DATE_FILTER ->
                    conversionRepository.findByIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                            transactionId,
                            fromInclusive,
                            toExclusive,
                            pageable
                    );
            case CLIENT_ID_FILTER + DATE_FILTER ->
                    conversionRepository.findByClient_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                            clientId,
                            fromInclusive,
                            toExclusive,
                            pageable
                    );
            case TRANSACTION_ID_FILTER -> conversionRepository.findByIdIs(transactionId, pageable);
            case CLIENT_ID_FILTER -> conversionRepository.findByClient_Id(clientId, pageable);
            case DATE_FILTER -> conversionRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                    fromInclusive,
                    toExclusive,
                    pageable
            );
            default -> throw new InvalidConversionSearchException();
        };
    }

    private void requireClient(String clientId) {
        if (!clientRepository.existsById(clientId)) {
            throw new ClientNotFoundException(clientId);
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new InvalidIdempotencyKeyException();
        }
        return idempotencyKey.trim();
    }

    private BigDecimal scaleMoney(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, ROUNDING_MODE);
    }
}

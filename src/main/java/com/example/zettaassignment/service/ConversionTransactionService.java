package com.example.zettaassignment.service;

import com.example.zettaassignment.domain.Balance;
import com.example.zettaassignment.domain.Client;
import com.example.zettaassignment.domain.Conversion;
import com.example.zettaassignment.dto.ConversionResponse;
import com.example.zettaassignment.exception.InsufficientFundsException;
import com.example.zettaassignment.repository.ClientRepository;
import com.example.zettaassignment.repository.ConversionRepository;
import com.example.zettaassignment.repository.IdempotencyLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConversionTransactionService {

    private final ClientRepository clientRepository;
    private final ConversionRepository conversionRepository;
    private final IdempotencyLockRepository idempotencyLockRepository;
    private final BalanceService balanceService;
    private final ConversionResponseMapper responseMapper;


    @Transactional
    public ConversionResponse applyConversion(
            String clientId,
            String idempotencyKey,
            String sourceCurrency,
            BigDecimal sourceAmount,
            String targetCurrency,
            BigDecimal targetAmount,
            BigDecimal rate
    ) {
        idempotencyLockRepository.lock(
                clientId,
                idempotencyKey
        );

        Map<String, Balance> lockedBalances = lockBalances(clientId, sourceCurrency, targetCurrency);
        Balance sourceBalance = lockedBalances.get(sourceCurrency);
        Balance targetBalance = lockedBalances.get(targetCurrency);

        Conversion existingConversion = conversionRepository
                .findByClient_IdAndIdempotencyKey(clientId, idempotencyKey)
                .orElse(null);
        if (existingConversion != null) {
            return responseMapper.toResponse(existingConversion);
        }

        if (sourceBalance.getAmount().compareTo(sourceAmount) < 0) {
            throw new InsufficientFundsException(clientId, sourceCurrency);
        }

        sourceBalance.debit(sourceAmount);
        targetBalance.credit(targetAmount);

        Client client = clientRepository.getReferenceById(clientId);
        Conversion conversion = conversionRepository.save(new Conversion(
                client,
                sourceCurrency,
                sourceAmount,
                targetCurrency,
                targetAmount,
                rate,
                idempotencyKey
        ));

        return responseMapper.toResponse(conversion);
    }

    private Map<String, Balance> lockBalances(String clientId, String sourceCurrency, String targetCurrency) {
        if (sourceCurrency.equals(targetCurrency)) {
            Map<String, Balance> balances = new LinkedHashMap<>();
            balances.put(sourceCurrency, balanceService.getBalanceForUpdate(clientId, sourceCurrency));
            return balances;
        }

        List<String> currencies = List.of(sourceCurrency, targetCurrency).stream()
                .sorted()
                .toList();

        Map<String, Balance> balances = new LinkedHashMap<>();
        for (String currency : currencies) {
            balances.put(currency, balanceService.getBalanceForUpdate(clientId, currency));
        }
        return balances;
    }
}

package com.example.zettaassignment.service;

import com.example.zettaassignment.dto.BalanceResponse;
import com.example.zettaassignment.domain.Balance;
import com.example.zettaassignment.domain.BalanceId;
import com.example.zettaassignment.exception.BalanceNotFoundException;
import com.example.zettaassignment.exception.ClientNotFoundException;
import com.example.zettaassignment.repository.BalanceRepository;
import com.example.zettaassignment.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BalanceService {

    private final ClientRepository clientRepository;
    private final BalanceRepository balanceRepository;

    @Transactional(readOnly = true)
    public List<BalanceResponse> getBalances(String clientId) {
        requireClient(clientId);

        return balanceRepository.findByIdClientIdOrderByIdCurrency(clientId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Balance getBalanceForUpdate(String clientId, String currencyCode) {
        return balanceRepository.findByIdForUpdate(new BalanceId(clientId, currencyCode))
                .orElseThrow(() -> new BalanceNotFoundException(clientId, currencyCode));
    }

    private void requireClient(String clientId) {
        if (!clientRepository.existsById(clientId)) {
            throw new ClientNotFoundException(clientId);
        }
    }

    private BalanceResponse toResponse(Balance balance) {
        return new BalanceResponse(
                Currency.getInstance(balance.getCurrency()),
                balance.getAmount()
        );
    }
}

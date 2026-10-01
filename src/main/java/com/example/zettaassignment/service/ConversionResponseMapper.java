package com.example.zettaassignment.service;

import com.example.zettaassignment.domain.Conversion;
import com.example.zettaassignment.dto.ConversionHistoryResponse;
import com.example.zettaassignment.dto.ConversionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Currency;

@Component
@RequiredArgsConstructor
public class ConversionResponseMapper {

    private final BalanceService balanceService;

    public ConversionResponse toResponse(Conversion conversion) {
        return new ConversionResponse(
                conversion.getId(),
                conversion.getSourceAmount(),
                Currency.getInstance(conversion.getSourceCurrency()),
                conversion.getTargetAmount(),
                Currency.getInstance(conversion.getTargetCurrency()),
                conversion.getRate(),
                conversion.getCreatedAt(),
                balanceService.getBalances(conversion.getClientId())
        );
    }

    public ConversionHistoryResponse toHistoryResponse(Conversion conversion) {
        return new ConversionHistoryResponse(
                conversion.getId(),
                conversion.getClientId(),
                conversion.getSourceAmount(),
                Currency.getInstance(conversion.getSourceCurrency()),
                conversion.getTargetAmount(),
                Currency.getInstance(conversion.getTargetCurrency()),
                conversion.getRate(),
                conversion.getCreatedAt()
        );
    }
}

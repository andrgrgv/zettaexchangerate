package com.example.zettaassignment.controller;

import com.example.zettaassignment.dto.RateResponse;
import com.example.zettaassignment.service.RateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Currency;


@RestController
@RequestMapping("/rates")
@RequiredArgsConstructor
@Tag(name = "Rates")
public class RateController {

    private final RateService rateService;

    @Operation(summary = "Get the current exchange rate between two currencies")
    @GetMapping
    public RateResponse getRate(
            @Parameter(description = "Source ISO-4217 currency code", example = "USD")
            @RequestParam Currency from,
            @Parameter(description = "Target ISO-4217 currency code", example = "EUR")
            @RequestParam Currency to
    ) {
        return rateService.getRate(from, to);
    }
}

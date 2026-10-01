package com.example.zettaassignment.controller;

import com.example.zettaassignment.dto.ConversionRequest;
import com.example.zettaassignment.dto.ConversionResponse;
import com.example.zettaassignment.dto.ConversionHistoryResponse;
import com.example.zettaassignment.service.ConversionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/conversions")
@RequiredArgsConstructor
@Validated
@Tag(name = "Conversions")
public class ConversionController {

    private final ConversionService conversionService;

    @Operation(summary = "Convert funds for a client")
    @PostMapping
    public ConversionResponse convert(
            @Parameter(description = "Client identifier whose balances are used for the conversion")
            @RequestHeader("X-Client-Id") @NotBlank String clientId,
            @Parameter(description = "Required per-client idempotency key. Reusing the same key returns the original conversion.")
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody ConversionRequest request
    ) {
        return conversionService.convert(
                clientId,
                idempotencyKey,
                request
        );
    }

    @Operation(summary = "Search conversion history")
    @GetMapping
    public Page<ConversionHistoryResponse> getConversions(
            @Parameter(description = "Exact conversion transaction id")
            @RequestParam(required = false) UUID transactionId,
            @Parameter(description = "Conversion date in YYYY-MM-DD format")
            @RequestParam(required = false) LocalDate date,
            @Parameter(description = "Client identifier")
            @RequestParam(required = false) String clientId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return conversionService.findConversions(
                transactionId,
                date,
                clientId,
                pageable
        );
    }
}

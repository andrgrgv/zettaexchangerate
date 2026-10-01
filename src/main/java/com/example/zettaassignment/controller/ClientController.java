package com.example.zettaassignment.controller;

import com.example.zettaassignment.dto.BalanceResponse;
import com.example.zettaassignment.service.BalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
@Validated
@Tag(name = "Clients")
public class ClientController {

    private final BalanceService balanceService;

    @Operation(summary = "Get current balances for a client")
    @GetMapping("/{clientId}/balances")
    public List<BalanceResponse> getBalances(
            @Parameter(description = "Client identifier", example = "CLIENT-001")
            @PathVariable @NotBlank String clientId
    ) {
        return balanceService.getBalances(clientId);
    }
}

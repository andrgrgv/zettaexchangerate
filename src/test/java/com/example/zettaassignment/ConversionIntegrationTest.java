package com.example.zettaassignment;

import com.example.zettaassignment.dto.ConversionRequest;
import com.example.zettaassignment.dto.ConversionResponse;
import com.example.zettaassignment.dto.RateResponse;
import com.example.zettaassignment.provider.RateProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConversionIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void convertDebitsCreditsPersistsAndReplaysIdempotently() {
        ConversionRequest request = new ConversionRequest(
                Currency.getInstance("USD"),
                Currency.getInstance("EUR"),
                new BigDecimal("100.0000")
        );

        ResponseEntity<ConversionResponse> firstResponse = postConversion(
                "CLIENT-001",
                "integration-key-001",
                request
        );
        ResponseEntity<ConversionResponse> replayResponse = postConversion(
                "CLIENT-001",
                "integration-key-001",
                request
        );

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(replayResponse.getBody()).isNotNull();
        assertThat(replayResponse.getBody().transactionId())
                .isEqualTo(firstResponse.getBody().transactionId());
        assertThat(firstResponse.getBody().sourceAmount()).isEqualByComparingTo("100.0000");
        assertThat(firstResponse.getBody().targetAmount()).isEqualByComparingTo("92.0000");
        assertThat(firstResponse.getBody().rate()).isEqualByComparingTo("0.92000000");
        assertThat(firstResponse.getBody().balances())
                .anySatisfy(balance -> {
                    assertThat(balance.currency()).isEqualTo(Currency.getInstance("USD"));
                    assertThat(balance.amount()).isEqualByComparingTo("9900.0000");
                })
                .anySatisfy(balance -> {
                    assertThat(balance.currency()).isEqualTo(Currency.getInstance("EUR"));
                    assertThat(balance.amount()).isEqualByComparingTo("8092.0000");
                });
    }

    @Test
    void conversionWithInsufficientFundsReturns422AndDoesNotPersist() {
        ConversionRequest request = new ConversionRequest(
                Currency.getInstance("USD"),
                Currency.getInstance("EUR"),
                new BigDecimal("999999.0000")
        );

        ResponseEntity<String> response = restTemplate.exchange(
                "/conversions",
                HttpMethod.POST,
                new HttpEntity<>(request, headers("CLIENT-001", "integration-too-much")),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("INSUFFICIENT_FUNDS");
    }

    private ResponseEntity<ConversionResponse> postConversion(
            String clientId,
            String idempotencyKey,
            ConversionRequest request
    ) {
        return restTemplate.exchange(
                "/conversions",
                HttpMethod.POST,
                new HttpEntity<>(request, headers(clientId, idempotencyKey)),
                ConversionResponse.class
        );
    }

    private HttpHeaders headers(String clientId, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Client-Id", clientId);
        headers.set("Idempotency-Key", idempotencyKey);
        return headers;
    }

    @TestConfiguration
    static class FixedRateProviderConfig {

        @Bean
        @Primary
        RateProvider fixedRateProvider() {
            return (from, to) -> new RateResponse(
                    from.getCurrencyCode(),
                    to.getCurrencyCode(),
                    new BigDecimal("0.92000000"),
                    Instant.parse("2026-10-01T10:00:00Z")
            );
        }
    }
}

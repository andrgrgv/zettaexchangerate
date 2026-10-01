package com.example.zettaassignment.provider;

import com.example.zettaassignment.dto.RateResponse;
import com.example.zettaassignment.exception.RateProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FrankfurterRateProviderTest {

    private MockRestServiceServer server;
    private FrankfurterRateProvider rateProvider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.frankfurter.dev");
        server = MockRestServiceServer.bindTo(builder).build();
        rateProvider = new FrankfurterRateProvider(builder.build());
    }

    @Test
    void getRateReturnsParsedRate() {
        server.expect(requestTo("https://api.frankfurter.dev/v2/rate/USD/EUR"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"amount":1.0,"base":"USD","quote":"EUR","rate":0.92}
                        """, MediaType.APPLICATION_JSON));

        RateResponse response = rateProvider.getRate(
                Currency.getInstance("USD"),
                Currency.getInstance("EUR")
        );

        assertThat(response.from()).isEqualTo("USD");
        assertThat(response.to()).isEqualTo("EUR");
        assertThat(response.rate()).isEqualByComparingTo(new BigDecimal("0.92"));
        assertThat(response.timestamp()).isNotNull();
        server.verify();
    }

    @Test
    void getRateReturnsOneWithoutCallingProviderWhenCurrenciesMatch() {
        RateResponse response = rateProvider.getRate(
                Currency.getInstance("USD"),
                Currency.getInstance("USD")
        );

        assertThat(response.from()).isEqualTo("USD");
        assertThat(response.to()).isEqualTo("USD");
        assertThat(response.rate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(response.timestamp()).isNotNull();
        server.verify();
    }

    @Test
    void getRateThrowsWhenResponseDoesNotContainNumericRate() {
        server.expect(requestTo("https://api.frankfurter.dev/v2/rate/USD/EUR"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"amount":1.0,"base":"USD","quote":"EUR"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> rateProvider.getRate(
                Currency.getInstance("USD"),
                Currency.getInstance("EUR")
        ))
                .isInstanceOf(RateProviderException.class)
                .hasMessage("Frankfurter returned an invalid response");

        server.verify();
    }

    @Test
    void getRateWrapsRestClientFailure() {
        server.expect(requestTo("https://api.frankfurter.dev/v2/rate/USD/EUR"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> rateProvider.getRate(
                Currency.getInstance("USD"),
                Currency.getInstance("EUR")
        ))
                .isInstanceOf(RateProviderException.class)
                .hasMessage("Failed to fetch exchange rate from Frankfurter")
                .hasCauseInstanceOf(RuntimeException.class);

        server.verify();
    }
}

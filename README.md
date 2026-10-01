# Zetta FX Conversion API

Spring Boot service for live FX rates, balance-backed currency conversions and paginated conversion history.

## Stack

- Java 17
- Spring Boot 3.5
- Spring Web, Validation, Data JPA
- PostgreSQL
- Flyway
- Spring Cache with Caffeine
- SpringDoc OpenAPI
- Docker Compose
- Testcontainers for integration tests

## Run

Start the API and PostgreSQL:

```bash
docker compose up --build
```

The API runs on:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

To reset local demo data, remove the Compose volume and start again:

```bash
docker compose down -v
docker compose up --build
```

## Demo Data

Flyway seeds five demo clients.

| Client | Balances |
| --- | --- |
| `CLIENT-001` | `10000.0000 USD`, `8000.0000 EUR`, `2500.0000 GBP`, `1500.0000 CHF` |
| `CLIENT-002` | `5000.0000 GBP`, `3000.0000 USD`, `2000.0000 EUR`, `750000.0000 JPY` |
| `CLIENT-003` | `25000.0000 USD`, `12000.0000 EUR`, `18000.0000 CAD`, `9000.0000 AUD` |
| `CLIENT-004` | `50000.0000 EUR`, `20000.0000 CHF`, `15000.0000 GBP`, `10000.0000 USD` |
| `CLIENT-005` | `1000.0000 USD`, `750.0000 EUR`, `500.0000 GBP`, `4000.0000 PLN` |

## Example Requests

Get a rate:

```bash
curl "http://localhost:8080/rates?from=USD&to=EUR"
```

Get balances:

```bash
curl "http://localhost:8080/clients/CLIENT-001/balances"
```

Convert funds:

```bash
curl -X POST "http://localhost:8080/conversions" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: CLIENT-001" \
  -H "Idempotency-Key: demo-conversion-001" \
  -d '{"sourceCurrency":"USD","targetCurrency":"EUR","amount":100}'
```

Replay the same idempotency key:

```bash
curl -X POST "http://localhost:8080/conversions" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: CLIENT-001" \
  -H "Idempotency-Key: demo-conversion-001" \
  -d '{"sourceCurrency":"USD","targetCurrency":"EUR","amount":100}'
```

Search conversion history:

```bash
curl "http://localhost:8080/conversions?clientId=CLIENT-001&page=0&size=20"
```

Insufficient funds example:

```bash
curl -X POST "http://localhost:8080/conversions" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: CLIENT-001" \
  -H "Idempotency-Key: too-much" \
  -d '{"sourceCurrency":"USD","targetCurrency":"EUR","amount":999999}'
```

## API

### `GET /rates?from=USD&to=EUR`

Returns the current exchange rate between two ISO-4217 currencies.

### `POST /conversions`

Headers:

- `X-Client-Id`: required client identifier.
- `Idempotency-Key`: required per-client idempotency key.

Body:

```json
{
  "sourceCurrency": "USD",
  "targetCurrency": "EUR",
  "amount": 100
}
```

On success, the API returns the transaction id, source/target amounts, currencies, rate, timestamp and updated balances.

### `GET /conversions`

Supported filters:

- `transactionId`
- `date`, formatted as `YYYY-MM-DD`
- `clientId`
- `page`
- `size`

At least one of `transactionId`, `date` or `clientId` is required.

Returns paginated conversion history rows with transaction id, client id, source/target amounts, currencies, rate and timestamp. History rows do not include current balances.

### `GET /clients/{clientId}/balances`

Returns current balances for the client.

## Design Notes

### Money

Amounts use `BigDecimal`. Stored money scale is 4 decimal places and rates use 8 decimal places. Four decimal places are used to retain precision from FX calculations, not because every currency displays four decimals. In a production settlement flow, display and final rounding would likely use currency-specific minor-unit rules.

Incoming conversion amounts with more than 4 decimals are rejected by validation instead of rounded silently.

### Idempotency

`POST /conversions` requires `Idempotency-Key`. Idempotency is scoped per client with a database unique constraint on `(client_id, idempotency_key)`.

The service checks for an existing conversion before taking balance locks, then checks again after acquiring locks. The second check handles concurrent requests with the same key without double-debiting balances.

### Concurrency

Balance rows are locked with JPA pessimistic write locks. Source and target balances are locked in sorted currency order to reduce deadlock risk when two opposite conversions happen at the same time.

The exchange rate is resolved before entering the transactional balance update, so a slow provider call does not keep a database transaction or row locks open. The balance check and debit/credit still happen inside the locked transaction.

### Rate Provider

Rates are fetched from Frankfurter:

```text
https://api.frankfurter.dev
```

HTTP connect timeout is 2 seconds and read timeout is 5 seconds. Provider failures are wrapped in a domain exception and returned as a structured error response.

### Rate Cache

Rates are cached in memory with Caffeine for 60 seconds. This keeps rates reasonably fresh while avoiding repeated provider calls for hot pairs.

The timestamp in `RateResponse` is the time the rate was fetched, not the time a cached value is returned.

## Error Codes

Errors return a consistent body:

```json
{
  "code": "INSUFFICIENT_FUNDS",
  "message": "...",
  "timestamp": "...",
  "details": {}
}
```

Main codes:

- `CLIENT_NOT_FOUND` -> `404`
- `BALANCE_NOT_FOUND` -> `404`
- `INSUFFICIENT_FUNDS` -> `422`
- `RATE_PROVIDER_FAILURE` -> `502`
- `INVALID_CONVERSION_SEARCH` -> `400`
- `INVALID_IDEMPOTENCY_KEY` -> `400`
- `VALIDATION_ERROR` -> `400`
- `BAD_REQUEST` -> `400`

## Tests

Run all tests:

```bash
./gradlew test
```

The test suite includes:

- service unit tests
- provider unit tests with mocked HTTP
- controller MVC slice tests
- PostgreSQL Testcontainers integration test for conversion flow

The integration test uses a fixed test rate provider and does not call the real Frankfurter API.

## Tradeoffs and Next Steps

- Same-currency conversions are currently allowed and net to no balance change. In a production API, I would likely reject these as invalid input.
- Idempotency currently returns the original conversion for the same `(clientId, idempotencyKey)`. A stricter production version would also store a request hash and return a conflict if the same key is reused with a different request body.
- The rate cache is in-memory and process-local. For multiple app instances, a shared cache or quoted-rate model would be more predictable.
- Additional rate providers can be added behind the `RateProvider` interface using a strategy/fallback pattern, so the API can continue serving rates when the primary provider is unavailable.

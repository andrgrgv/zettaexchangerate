CREATE TABLE clients (
    id VARCHAR PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE balances (
    client_id VARCHAR NOT NULL,
    currency CHAR(3) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (client_id, currency),
    CONSTRAINT fk_balances_client
        FOREIGN KEY (client_id)
        REFERENCES clients (id)
);

CREATE TABLE conversions (
    id UUID PRIMARY KEY,
    client_id VARCHAR NOT NULL,
    source_currency CHAR(3) NOT NULL,
    source_amount DECIMAL(19, 4) NOT NULL,
    target_currency CHAR(3) NOT NULL,
    target_amount DECIMAL(19, 4) NOT NULL,
    rate DECIMAL(19, 8) NOT NULL,
    idempotency_key VARCHAR NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_conversions_client
        FOREIGN KEY (client_id)
        REFERENCES clients (id),
    CONSTRAINT uq_conversions_client_idempotency_key
        UNIQUE (client_id, idempotency_key)
);

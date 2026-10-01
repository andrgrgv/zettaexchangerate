package com.example.zettaassignment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "conversions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_conversions_client_idempotency_key",
                columnNames = {"client_id", "idempotency_key"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversion {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "source_currency", nullable = false, length = 3)
    private String sourceCurrency;

    @Column(name = "source_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal sourceAmount;

    @Column(name = "target_currency", nullable = false, length = 3)
    private String targetCurrency;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;

    @Column(name = "rate", nullable = false, precision = 19, scale = 8)
    private BigDecimal rate;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Conversion(
            Client client,
            String sourceCurrency,
            BigDecimal sourceAmount,
            String targetCurrency,
            BigDecimal targetAmount,
            BigDecimal rate,
            String idempotencyKey
    ) {
        this.id = UUID.randomUUID();
        this.client = client;
        this.sourceCurrency = sourceCurrency;
        this.sourceAmount = sourceAmount;
        this.targetCurrency = targetCurrency;
        this.targetAmount = targetAmount;
        this.rate = rate;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }

    public String getClientId() {
        return client.getId();
    }
}

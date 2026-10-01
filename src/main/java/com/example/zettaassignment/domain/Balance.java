package com.example.zettaassignment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "balances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Balance {

    @EmbeddedId
    private BalanceId id;

    @MapsId("clientId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Balance(Client client, String currency, BigDecimal amount) {
        this.client = client;
        this.id = new BalanceId(client.getId(), currency);
        this.amount = amount;
    }

    public String getClientId() {
        return id.getClientId();
    }

    public String getCurrency() {
        return id.getCurrency();
    }

    public void debit(BigDecimal amount) {
        this.amount = this.amount.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        this.amount = this.amount.add(amount);
    }
}

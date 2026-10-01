package com.example.zettaassignment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BalanceId implements Serializable {

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    public BalanceId(String clientId, String currency) {
        this.clientId = clientId;
        this.currency = currency;
    }
}

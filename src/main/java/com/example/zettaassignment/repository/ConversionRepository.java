package com.example.zettaassignment.repository;

import com.example.zettaassignment.domain.Conversion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ConversionRepository extends JpaRepository<Conversion, UUID> {

    Optional<Conversion> findByClient_IdAndIdempotencyKey(String clientId, String idempotencyKey);

    Page<Conversion> findByIdIs(UUID id, Pageable pageable);

    Page<Conversion> findByClient_Id(String clientId, Pageable pageable);

    Page<Conversion> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable
    );

    Page<Conversion> findByIdAndClient_Id(UUID id, String clientId, Pageable pageable);

    Page<Conversion> findByIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID id,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable
    );

    Page<Conversion> findByClient_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            String clientId,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable
    );

    Page<Conversion> findByIdAndClient_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID id,
            String clientId,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable
    );
}

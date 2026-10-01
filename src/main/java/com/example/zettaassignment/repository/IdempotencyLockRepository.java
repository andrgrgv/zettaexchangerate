package com.example.zettaassignment.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class IdempotencyLockRepository {

    private final EntityManager entityManager;

    public void lock(String clientId, String idempotencyKey) {
        String lockKey = clientId + ":" + idempotencyKey;

        entityManager.createNativeQuery("""
                        SELECT pg_advisory_xact_lock(
                            hashtextextended(:lockKey, 0)
                        )
                        """)
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }
}

package com.example.zettaassignment.repository;

import com.example.zettaassignment.domain.Balance;
import com.example.zettaassignment.domain.BalanceId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BalanceRepository extends JpaRepository<Balance, BalanceId> {

    List<Balance> findByIdClientIdOrderByIdCurrency(String clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Balance b where b.id = :id")
    Optional<Balance> findByIdForUpdate(@Param("id") BalanceId id);
}

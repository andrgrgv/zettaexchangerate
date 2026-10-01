package com.example.zettaassignment.repository;

import com.example.zettaassignment.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, String> {
}

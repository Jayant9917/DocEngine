package com.docengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<TenantRecord, UUID> {
    Optional<TenantRecord> findByApiKey(String apiKey);
}

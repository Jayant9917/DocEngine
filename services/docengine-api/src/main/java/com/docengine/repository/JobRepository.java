package com.docengine.repository;

import com.docengine.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
    Optional<Job> findByIdAndTenantId(UUID id, UUID tenantId);
}

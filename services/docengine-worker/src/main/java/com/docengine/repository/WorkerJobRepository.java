package com.docengine.repository;

import com.docengine.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface WorkerJobRepository extends JpaRepository<Job, UUID> {
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE jobs
            SET status = 'PROCESSING',
                claimed_by = :workerId,
                attempt_count = attempt_count + 1,
                started_at = COALESCE(started_at, CURRENT_TIMESTAMP),
                lease_until = CURRENT_TIMESTAMP + (:leaseSeconds * INTERVAL '1 second')
            WHERE id = :jobId
              AND (status = 'QUEUED'
                   OR (status = 'PROCESSING' AND lease_until IS NOT NULL AND lease_until < CURRENT_TIMESTAMP))
            """, nativeQuery = true)
    int claim(@Param("jobId") UUID jobId, @Param("workerId") String workerId,
              @Param("leaseSeconds") int leaseSeconds);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE jobs
            SET lease_until = CURRENT_TIMESTAMP + (:leaseSeconds * INTERVAL '1 second')
            WHERE id = :jobId AND status = 'PROCESSING' AND claimed_by = :workerId
            """, nativeQuery = true)
    int renewLease(@Param("jobId") UUID jobId, @Param("workerId") String workerId,
                   @Param("leaseSeconds") int leaseSeconds);
}

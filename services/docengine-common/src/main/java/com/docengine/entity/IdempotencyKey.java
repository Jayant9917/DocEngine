package com.docengine.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    private UUID id;

    private UUID tenantId;
    private String idempotencyKey;
    private UUID jobId;
    private String requestHash;

    protected IdempotencyKey() {
        // Required by JPA.
    }

    public IdempotencyKey(UUID id, UUID tenantId, String idempotencyKey,
                          UUID jobId, String requestHash) {
        this.id = id;
        this.tenantId = tenantId;
        this.idempotencyKey = idempotencyKey;
        this.jobId = jobId;
        this.requestHash = requestHash;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getJobId() { return jobId; }
    public String getRequestHash() { return requestHash; }
}

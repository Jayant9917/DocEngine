CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    job_id UUID NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_idempotency_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_idempotency_job
        FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT uq_idempotency_tenant_key
        UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX idx_idempotency_job_id ON idempotency_keys (job_id);

CREATE TABLE jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    job_type VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    input_reference TEXT NOT NULL,
    result_reference TEXT,
    claimed_by VARCHAR(200),
    lease_until TIMESTAMP,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    error_details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,

    CONSTRAINT fk_jobs_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT chk_jobs_status
        CHECK (status IN ('CREATED', 'QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_jobs_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE INDEX idx_jobs_tenant_id ON jobs (tenant_id);
CREATE INDEX idx_jobs_status ON jobs (status);

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    api_key VARCHAR(200) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Development-only tenant for the MVP walkthrough.
INSERT INTO tenants (id, name, api_key)
VALUES ('00000000-0000-0000-0000-000000000001', 'Demo Tenant', 'demo-tenant-key');

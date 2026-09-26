# DocEngine MVP Architecture

## Scope

The MVP proves asynchronous job acceptance, PostgreSQL atomic claiming, API idempotency, tenant isolation, and CSV-uploaded report generation. It uses five containers and one instance of each application service.

## Topology

```text
Client
  |
  v
docengine-api :8080 (published on host as :8081)
  |             |              |
  v             v              v
PostgreSQL   RabbitMQ       MinIO
  ^             ^              ^
  |             |              |
  +------ docengine-worker ---+
              |
              v
       CSV parser/report processor
```

## Containers

| Container | Responsibility |
|---|---|
| `postgres` | Durable tenants, jobs, and idempotency records |
| `rabbitmq` | Delivers job messages to workers |
| `minio` | Stores private input CSVs and generated results |
| `docengine-api` | Authenticates, uploads, creates jobs, publishes messages, serves status |
| `docengine-worker` | Claims jobs, downloads CSVs, generates results, updates status |

## Storage buckets and tables

MinIO buckets:

- `docengine-inputs`: uploaded client CSV files
- `docengine-results`: generated PDF/CSV reports

PostgreSQL tables:

- `tenants` / API-key mapping used by the static MVP authentication mechanism
- `jobs`: job state, tenant, input reference, result reference, and claim data
- `idempotency_keys`: unique `(tenant_id, idempotency_key)` mapping

## Who writes what

| Data | API | Worker |
|---|---:|---:|
| Input object | writes | reads |
| Job row | creates | claims and updates |
| Idempotency row | creates/reads | does not write |
| Result object | does not write | writes |
| Signed result URL | creates on status response | does not create |

## Core correctness flow

1. API authenticates the API key and derives `tenant_id` server-side.
2. API stores the uploaded CSV in the tenant-scoped MinIO input path.
3. API inserts the job and idempotency record using the database unique constraint.
4. API publishes a message containing the job ID.
5. Worker runs `UPDATE jobs SET status='PROCESSING' ... WHERE status='QUEUED'`.
6. A worker that updates zero rows acknowledges and drops the duplicate delivery.
7. The winner downloads the CSV, generates the report, stores the result, and marks the job `COMPLETED` before acknowledging.

## Deferred infrastructure

Nginx, Redis, replicas, Prometheus, Grafana, OpenTelemetry, automated retries/DLQ, leases, and external secret management are deferred. The interfaces around storage, messaging, processing, and persistence should remain replaceable so these can be added without changing the API contract.

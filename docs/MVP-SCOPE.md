# DocEngine — MVP Scope

Goal: prove the core design (async accept, atomic claim, idempotency, tenant isolation) works
end-to-end, with the smallest possible footprint. Everything else from the full docs is real
and correct — just deferred, not deleted.

---

## 1. What's IN the MVP

- One job type: `GENERATE_MONTHLY_REPORT` (CSV in → PDF or CSV out).
- Input data comes from a client-uploaded CSV file. The MVP does not connect to a real client
  database or reporting API.
- One API container, one Worker container (no replicas yet — that's Phase 4, not now).
- Async accept: `POST /jobs` → `202 + jobId`, real processing happens in the worker.
- Atomic job claim in Postgres — this is the one mechanism you must not skip, it's the entire
  point of the project.
- API idempotency via `Idempotency-Key` header + DB unique constraint.
- Result stored in MinIO, delivered via short-lived signed URL, polling only.
- Tenant isolation kept simple but real: every job has `tenant_id`, every query filters by it.
  Auth simplified to a static API-key-to-tenant lookup table (no Spring Security OAuth/JWT
  complexity yet) — still correctly derives tenant server-side, never trusts the request body.
- Flyway migrations from day one (cheap to add now, painful to bolt on later).
- Testcontainers test proving 2+ workers racing a claim → exactly one wins. This is your proof
  the hard part actually works — don't skip it even in MVP.

## 2. What's OUT of the MVP (deferred, documented, not forgotten)

| Cut for now | Why safe to cut | Add back in |
|---|---|---|
| Nginx | Only 1 API container, nothing to load-balance yet | Phase 4 (multi-replica) |
| Redis | Explicitly not correctness-critical per design (rate limit/lock only) | Phase 3 (rate limiting) |
| Prometheus/Grafana/OTel | Observability matters at scale, not for proving the design | Phase 4 |
| Multiple API/worker replicas | MVP proves correctness with 1 replica, scale test comes later | Phase 4 |
| SonarQube/OWASP/Trivy scans | CI hardening, not core functionality | Phase 3-4, or a CI badge early if easy |
| k6 load testing | Nothing to load-test meaningfully with 1 replica | Phase 4 |
| Webhook/email/SSE delivery | Polling is enough to prove the concept | Phase 5 |
| JWT/OAuth, real secrets manager | Static API-key table is enough to prove tenant isolation | Phase 3 |
| Retry/RETRY_WAIT/DLQ automation | Keep the states in the schema, wire up FAILED handling manually first | Phase 2 |
| Lease/heartbeat reaper | Nice-to-have recovery; note the gap, don't block MVP on it | Phase 2 |
| Outbox pattern | Only needed once you have external side effects (email/webhook) | Phase 5 |

## 3. MVP Containers (5 total)

```
postgres         <- durable job state
rabbitmq         <- task queue
minio            <- result file storage
docengine-api     <- 1 container
docengine-worker  <- 1 container
```

No Nginx, no Redis, no Prometheus/Grafana stack. `docker-compose.mvp.yml` reflects exactly
this — 5 services, nothing more.

## 4. MVP API Surface

```
POST   /api/v1/uploads              -> multipart CSV upload; returns inputReference
POST   /api/v1/jobs                 -> 202 {jobId, status}   [Idempotency-Key header required]
GET    /api/v1/jobs/{jobId}         -> status + signed resultUrl when COMPLETED
GET    /actuator/health
```

Upload flow:

```text
1. POST /api/v1/uploads with a multipart CSV file
2. API stores the file in MinIO's private inputs bucket
3. API returns inputReference
4. POST /api/v1/jobs with jobType, month, and inputReference
5. Worker downloads and parses the CSV, then generates the report
```

Example job request:

```json
{
  "jobType": "GENERATE_MONTHLY_REPORT",
  "input": {
    "month": "2026-09",
    "dataFile": "s3://docengine-inputs/{tenantId}/{uploadId}/september-sales.csv"
  }
}
```

Skip for MVP: `GET /jobs` (list), `retry`, `cancel`, `/actuator/prometheus` — add once the core
loop works. Not hard to add, just not needed to prove the design.

## 5. MVP Input Data

The report data is client-supplied. For the local demo and automated tests, generate a
reproducible fake CSV fixture with 20–50 rows.

CSV columns:

```text
orderId,customer,amount,date
ORD-1001,Acme Co,5000.00,2026-09-14
```

Use Java DataFaker (`net.datafaker:datafaker:2.4.1`) or a checked-in static CSV file. The
recommended fixture is `september-sales.csv` with 40 rows and a fixed random seed (`42`). Keep a
copy under `tests/integration/resources/` so the same input is used by Testcontainers and the
manual demo.

The worker must validate the required columns, parse the amount and ISO date, filter rows for the
requested month, and produce the PDF or CSV result. No live client database connector is part of
the MVP; that is deferred to a later enterprise phase.

## 6. MVP DB Schema (same as full LLD, nothing trimmed here — schema is cheap to get right early)

```sql
-- jobs
id UUID PK
tenant_id UUID NOT NULL
job_type VARCHAR NOT NULL
status VARCHAR NOT NULL          -- CREATED/QUEUED/PROCESSING/COMPLETED/FAILED
input_reference TEXT
result_reference TEXT
claimed_by VARCHAR
lease_until TIMESTAMP
attempt_count INT DEFAULT 0
error_details TEXT
created_at, started_at, completed_at TIMESTAMP

-- idempotency_keys
tenant_id UUID
idempotency_key VARCHAR
job_id UUID
request_hash VARCHAR
UNIQUE(tenant_id, idempotency_key)
```

`job_attempts` table can wait for Phase 2 — nice for debugging, not required to prove the claim
mechanism works (you can prove that with the `attempt_count` column + Testcontainers test alone).

## 7. MVP Flow (identical logic to full design, just fewer moving parts)

```
Client -> API: POST /uploads (multipart CSV)
API -> MinIO: store private input file
API -> Client: inputReference

Client -> API: POST /jobs (Idempotency-Key, inputReference, tenant resolved from static API-key table)
API -> Postgres: check idempotency key -> insert job (CREATED->QUEUED)
API -> RabbitMQ: publish job message
API -> Client: 202 {jobId, status: QUEUED}

RabbitMQ -> Worker: deliver job message
Worker -> Postgres: atomic UPDATE ... WHERE status='QUEUED' (claim)
  0 rows -> ack & drop
  1 row  -> proceed
Worker: download CSV from MinIO -> parse/filter rows -> generate report -> upload result to MinIO
Worker: COMPLETED -> ACK

Client -> API: GET /jobs/{id} (poll) -> COMPLETED + signed download URL
```

## 8. MVP Build Order (fastest path to a working demo)

1. Flyway migration for `jobs` + `idempotency_keys` tables.
2. `docengine-common`: `Job` entity, `JobStatus` enum, `JobRequest`/`JobResponse` DTOs, `JobMessage`.
3. `docengine-api`: static API-key→tenant map, `POST /uploads` multipart handling, MinIO input
   upload, `POST /jobs` with idempotency insert, `GET /jobs/{id}`, and RabbitMQ publish.
4. `docengine-worker`: `@RabbitListener`, atomic claim query, MinIO download, CSV parsing, one
   `GenerateMonthlyReportProcessor`, MinIO result upload, status update, and ACK.
5. Add the reproducible 40-row `september-sales.csv` fixture and use it in the demo and tests.
6. Testcontainers test: spin up 2 worker instances (or 2 threads simulating workers) racing the
   same job row, assert exactly one claims it.
7. Manual end-to-end test via curl: upload CSV → submit job → poll → download.
8. Only then: decide what from the "OUT" list to add next, based on what your demo/interview
   actually needs.

## 9. Definition of Done for MVP

Upload the reproducible CSV → receive an `inputReference` → submit a job with an `Idempotency-Key`
and that reference → get `202` + `jobId` → worker downloads the CSV and claims the job → report is
generated and lands in MinIO → status flips to `COMPLETED` → poll returns a working signed download
URL → resubmitting the same `Idempotency-Key` returns the same job, not a new one → killing the
worker mid-job and starting a second worker container proves no duplicate processing (claim
mechanism holds).

That's the whole MVP. Everything in the full docs still applies once you're past this point —
nothing here contradicts them, it's just sequencing.

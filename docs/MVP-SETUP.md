# DocEngine MVP Setup

This guide runs the five-container MVP: PostgreSQL, RabbitMQ, MinIO, API, and worker.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop with Compose
- `curl`

## Start the infrastructure

From the repository root:

```bash
docker compose -f docs/docker-compose.mvp.yml up -d postgres rabbitmq minio
```

Useful local UIs:

- RabbitMQ: `http://127.0.0.1:15672`
- MinIO: `http://127.0.0.1:9001`
- API: `http://127.0.0.1:8081`
- PostgreSQL from Windows tools: `127.0.0.1:5433`
- PostgreSQL from Docker services: `postgres:5432`

Default local credentials are defined in `docs/docker-compose.mvp.yml`. Change them through environment variables before sharing the setup.

## Database and MinIO initialization

Flyway runs from the API application's startup configuration. If migrations are run separately, execute them before starting the worker.

Create the private buckets once using the MinIO client or the MinIO console:

```text
docengine-inputs
docengine-results
```

The API should create these buckets on startup when they do not exist, or fail fast with a clear setup error.

## Generate demo data

Generate a deterministic `september-sales.csv` fixture with 40 rows and columns:

```text
orderId,customer,amount,date
```

Use the DataFaker dependency `net.datafaker:datafaker:2.4.1` and random seed `42`, or keep a checked-in CSV under `tests/integration/resources/`.

## Build and start the applications

```bash
mvn clean package
docker compose -f docs/docker-compose.mvp.yml up -d --build docengine-api docengine-worker
```

Check the API:

```bash
curl http://127.0.0.1:8081/actuator/health
```

## End-to-end walkthrough

Upload the CSV:

```bash
curl -X POST http://127.0.0.1:8081/api/v1/uploads \
  -H "X-API-Key: demo-tenant-key" \
  -F "file=@september-sales.csv"
```

Copy the returned `inputReference` into the job request:

```bash
curl -X POST http://127.0.0.1:8081/api/v1/jobs \
  -H "X-API-Key: demo-tenant-key" \
  -H "Idempotency-Key: september-report-2026-09" \
  -H "Content-Type: application/json" \
  -d '{"jobType":"GENERATE_MONTHLY_REPORT","input":{"month":"2026-09","dataFile":"INPUT_REFERENCE_HERE"}}'
```

Poll using the returned job ID:

```bash
curl http://127.0.0.1:8081/api/v1/jobs/JOB_ID_HERE \
  -H "X-API-Key: demo-tenant-key"
```

When `status` is `COMPLETED`, download the result using the returned `resultUrl`.

## Idempotency check

Repeat the exact submit request with the same `Idempotency-Key`. It must return the same `jobId` and must not create another job.

## Two-worker claim check

```bash
docker compose -f docs/docker-compose.mvp.yml up -d --scale docengine-worker=2
```

Submit one job and inspect the database/logs. Exactly one worker must transition the job from `QUEUED` to `PROCESSING`; the other delivery must observe zero affected rows and acknowledge without executing the processor.

## Stop and clean up

```bash
docker compose -f docs/docker-compose.mvp.yml down
```

To remove MVP data as well, use `docker compose ... down -v` only when you intentionally want to delete the PostgreSQL and MinIO volumes.

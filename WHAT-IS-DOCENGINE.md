# What Is DocEngine?

DocEngine is a distributed background-job system for accepting and processing many long-running requests safely.

For example, a user can upload a sales CSV file and request a monthly report. Instead of making the user wait while the report is created, DocEngine accepts the request quickly, puts the job in a queue, and processes it in the background.

## How it works

1. The user uploads a CSV file.
2. DocEngine stores the file in MinIO, which acts like local S3 storage.
3. The user creates a report job through the API.
4. The API places the job message in RabbitMQ.
5. A worker receives the message and processes the CSV file.
6. The worker creates a CSV and PDF report.
7. The result files are stored in MinIO.
8. The user checks the job status through the API.
9. When the job is complete, the API provides a temporary download URL for the report.

## Main parts of the project

- **React frontend**: provides the web page where users upload files and interact with the system.
- **DocEngine API**: receives uploads, creates jobs, and reports job status.
- **DocEngine worker**: performs the time-consuming report-generation work.
- **PostgreSQL**: stores tenants, uploads, jobs, and job status.
- **RabbitMQ**: transports job messages from the API to workers.
- **MinIO**: stores uploaded files and generated reports.
- **pgAdmin**: provides a browser interface for inspecting PostgreSQL during local development.

## Why use a worker and a queue?

The API should respond quickly. Report generation may take time, so the API does not perform all the work itself. It creates a job and lets a worker process it separately.

This makes the system more reliable and scalable. If more jobs arrive, more worker containers can be started to process jobs in parallel.

## What happens when many users send requests at the same time?

The API does not wait for one report to finish before accepting the next request. Each request is handled independently:

1. The API receives the request and quickly stores its job record in PostgreSQL.
2. The API publishes a message to RabbitMQ and returns a `jobId`.
3. Workers consume different messages and process jobs in parallel.
4. PostgreSQL transactions and constraints protect shared data from conflicting updates.

For example, 100 users can submit jobs at nearly the same time. The API accepts the requests, RabbitMQ holds the work, and the workers process the queue according to available capacity. Users do not need to keep an HTTP request open while a report is generated.

## How the system avoids corruption and duplicate work

- **Atomic claiming**: before processing, a worker changes a job from `QUEUED` to `PROCESSING` using one database update. If two workers receive the same message, only one update succeeds; the other worker does not process the job.
- **Idempotency**: a client can safely retry a request with the same `Idempotency-Key`. The API returns the existing job instead of creating a duplicate.
- **Database transactions**: related database changes succeed together or roll back together.
- **Tenant isolation**: every job is associated with a tenant. API queries filter by both tenant and job ID, so one tenant cannot access another tenant's job.
- **Lease and heartbeat recovery**: workers renew ownership while processing. If a worker stops responding, an expired job can become available for recovery.
- **Durable storage**: uploaded input and generated results are stored outside the application process in MinIO/S3-compatible storage.

## How the API scales

The API service is designed to be stateless: it keeps durable state in PostgreSQL, RabbitMQ, and MinIO rather than in one API container's memory. That means multiple API containers can run at the same time behind a load balancer or reverse proxy.

For example:

```text
Users -> Load Balancer -> API 1
                     -> API 2
                     -> API 3
```

All API instances use the same database, queue, and object storage. A request may reach any API instance and still find the correct job because the job data is shared in PostgreSQL.

## How the worker scales

Workers are consumers of the same RabbitMQ queue. More worker containers increase processing capacity:

```text
RabbitMQ queue -> Worker 1
               -> Worker 2
               -> Worker 3
               -> Worker 4
```

RabbitMQ distributes messages among available workers, while the atomic database claim protects against duplicate processing if a message is delivered again.

## Important scope clarification

The project is architected as a scalable distributed system, and the MVP proves the important concurrency behavior locally with Docker and multiple workers. Production scaling still requires operational components such as a load balancer/reverse proxy, managed PostgreSQL/RabbitMQ/object storage, monitoring, alerting, rate limits, and deployment automation. Those are deployment concerns around the core application, not a reason to redesign the API and worker flow.

## Important features

- Background processing through RabbitMQ
- Atomic job claiming to prevent duplicate processing
- Idempotency so retrying the same request does not create duplicate jobs
- Tenant isolation so one tenant cannot access another tenant's jobs
- CSV upload validation
- CSV and PDF report generation
- Temporary result download URLs
- Worker lease and heartbeat recovery for failed or slow workers
- Docker-based local development
- Multiple API instances can share the same durable backend services
- Multiple worker instances can process queued jobs in parallel

## Simple explanation to tell someone else

> DocEngine is a scalable distributed background-job system. Many users can submit requests at the same time without waiting for reports to finish. The API stores and queues each request, multiple workers process jobs safely in parallel, and the finished CSV/PDF report is stored and made available through a temporary download link.

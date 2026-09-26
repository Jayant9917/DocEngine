# DocEngine

## Distributed Document Processing Platform

DocEngine is a distributed document-processing platform for accepting document-generation requests, placing them on a durable queue, processing them asynchronously with specialized workers, and storing the generated documents in object storage.

The platform is designed for workloads such as invoices, reports, certificates, statements, bulk PDF generation, and customer notifications. The current implementation demonstrates the flow with CSV uploads and monthly PDF/CSV report generation.

The application is structured as a distributed document-processing platform even though the current running configuration contains one API instance and one report-processing worker instance. The API and worker can be replicated when traffic, queue depth, or document volume increases.

## What the application does

DocEngine separates request handling from document generation:

- The API accepts an authenticated request and returns quickly with a job identifier.
- Uploaded source data is stored in MinIO/S3-compatible object storage.
- PostgreSQL stores tenants, job metadata, status, idempotency records, and processing information.
- RabbitMQ provides the durable handoff between the API and background workers.
- Workers claim jobs safely, generate documents, and store the results in object storage.
- Clients poll job status and receive a short-lived download URL when the document is ready.
- Prometheus and Grafana provide application, JVM, HTTP, database-pool, and worker-pipeline visibility.

## End-to-end flow

```text
Client
  |
  v
API Gateway / Spring API
  |-- authenticate tenant and validate request
  |-- store input in object storage
  |-- create job metadata in PostgreSQL
  |-- publish job message to RabbitMQ
  v
RabbitMQ queue
  |
  +--> PDF Worker
  +--> Report Worker
  +--> Invoice Worker
  +--> Certificate Worker
  +--> Email / Notification Worker
              |
              v
       Object Storage (S3 / MinIO)
              |
              v
       Status update + signed download URL
```

The worker types shown above represent the platform's extensible processing model. The current MVP includes the report-processing worker and can be extended with additional processors without changing the API-to-queue pattern.

## Supported document-processing model

The job model can represent multiple document workloads:

```text
INVOICE
REPORT
CERTIFICATE
STATEMENT
BULK_PDF
```

Each job type can be routed to a dedicated worker or queue. This keeps document-specific logic isolated and allows each worker group to scale independently.

Typical templates include:

- Invoice template
- Monthly report template
- Certificate template
- Account statement template

## Reliability and correctness

DocEngine is designed around the failure modes of asynchronous processing:

### Priority

Jobs can be classified as `HIGH`, `NORMAL`, or `LOW` priority. For example, payment invoices can be processed ahead of monthly analytics reports.

### Retries and dead-letter handling

Transient worker failures can be retried with a bounded retry policy. Jobs that continue to fail are moved to a dead-letter queue for inspection and controlled replay.

```text
Worker failure
      |
      v
   Retry 1 --> Retry 2 --> Retry 3
                                  |
                                  v
                         Dead Letter Queue
```

### Idempotency

An idempotency key prevents duplicate submissions from generating the same document more than once. If a client repeats a request with the same key, the API returns the existing job result instead of creating duplicate work.

### Safe worker claiming

Multiple workers can consume jobs concurrently. PostgreSQL-backed atomic claiming prevents two workers from processing the same job at the same time. Lease and heartbeat behavior allows abandoned work to be recovered after a worker failure.

### Job lifecycle

```text
QUEUED -> PROCESSING -> COMPLETED
             |
             +-> RETRYING -> PROCESSING
             |
             +-> FAILED
             |
             +-> CANCELLED
```

Progress can be exposed as milestones such as `0%`, `25%`, `50%`, `75%`, and `100%` for long-running or bulk document jobs.

## Horizontal scaling

The API and workers are independently scalable:

```text
                  RabbitMQ
                 /    |    \
                /     |     \
          Worker-1 Worker-2 Worker-3
                              |
                           Worker-4
```

When traffic increases, more worker replicas can be added without changing the client or API contract. Different worker pools can also be scaled according to workload—for example, more invoice workers during billing periods and more report workers at month-end.

This design avoids making the API perform expensive document generation synchronously. The API remains responsive while the queue absorbs bursts and workers process jobs at a controlled rate.

## Large-scale enterprise scenario

Consider a bank generating monthly statements for millions of customers:

```text
Monthly scheduler / job producer
              |
              v
          RabbitMQ
       /      |      \
      v       v       v
 Worker-1  Worker-2  Worker-3  ... Worker-N
       \      |      /
              v
         S3 / MinIO
              |
              v
        Customer notification
```

Instead of one server generating statements sequentially, the system creates independent jobs and distributes them across worker replicas. PostgreSQL tracks job state, object storage holds the documents, RabbitMQ controls delivery, and monitoring shows throughput, latency, failures, retries, and queue pressure.

## Bottlenecks to evaluate

When an error or performance issue appears, the next bottleneck is usually in one of these areas:

- API request rate, thread pools, validation, or upload bandwidth
- RabbitMQ queue depth, consumer rate, acknowledgements, or retry storms
- PostgreSQL connections, locks, slow queries, or job-claim contention
- Worker CPU, memory, PDF rendering time, or concurrent processing limit
- Object-storage throughput, disk capacity, or network latency
- Notification provider rate limits and downstream failures
- Monitoring and logging capacity during high-volume incidents

The architecture makes these boundaries visible so each layer can be measured and scaled independently.

## Technology responsibilities

| Component | Responsibility |
|---|---|
| React frontend | Upload data, submit jobs, display status, download results |
| Spring Boot API | Authentication, validation, uploads, job creation, status APIs |
| PostgreSQL | Tenants, jobs, idempotency, status, and processing metadata |
| RabbitMQ | Durable asynchronous job delivery and workload buffering |
| Worker services | Document-specific processing and result generation |
| MinIO / S3 | Source files and generated document storage |
| Docker Compose | Reproducible service orchestration |
| Prometheus | Metrics collection |
| Grafana | Dashboards and operational visibility |

## Current deployment shape and scaling model

The current working implementation runs with one API instance and one PDF/report worker instance:

```text
Client -> API instance -> RabbitMQ -> PDF/report worker instance
```

The services can scale horizontally when required:

```text
                    +--> API instance 1 --+
Client --> Load Balancer +--> API instance 2 --+--> RabbitMQ
                    +--> API instance N --+       |
                                                   +--> Worker 1
                                                   +--> Worker 2
                                                   +--> Worker N
```

More API instances can be added behind a load balancer to handle incoming requests. More worker instances can be added to process queued documents in parallel. Separate worker pools can later be introduced for invoices, certificates, statements, bulk PDFs, and notifications.

## Running the application

### Requirements

- Docker Desktop with Docker Compose
- Java 21
- Maven 3.9 or newer
- Node.js and npm
- Git

### Start the backend services

```powershell
Copy-Item .env.example .env
docker compose --env-file .env -f docs/docker-compose.mvp.yml up -d --build
```

The stack includes PostgreSQL, RabbitMQ, MinIO, the DocEngine API, the document worker, Prometheus, Grafana, and pgAdmin.

### Start the frontend

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` and use the demo tenant key documented in [SETUP.md](../SETUP.md).

### Verify the services

```powershell
docker compose --env-file .env -f docs/docker-compose.mvp.yml ps
curl.exe http://127.0.0.1:8081/actuator/health
```

The API health endpoint should return a healthy status. The complete upload-to-report flow is documented in [SETUP.md](../SETUP.md).

## Service addresses

| Service | Address |
|---|---|
| Frontend | `http://localhost:5173` |
| DocEngine API | `http://127.0.0.1:8081` |
| API health | `http://127.0.0.1:8081/actuator/health` |
| API metrics | `http://127.0.0.1:8081/actuator/prometheus` |
| Worker metrics | `http://127.0.0.1:8082/actuator/prometheus` |
| Prometheus | `http://127.0.0.1:9090` |
| Grafana | `http://127.0.0.1:3000` |
| RabbitMQ management | `http://127.0.0.1:15672` |
| MinIO console | `http://127.0.0.1:9001` |
| pgAdmin | `http://127.0.0.1:5050` |

## Project layout

```text
services/
  docengine-api/       Spring Boot HTTP API
  docengine-worker/    RabbitMQ document worker
  docengine-common/    Shared domain and messaging types
frontend/              React + Vite user interface
database/migrations/   Flyway SQL migrations
docs/                  Architecture, API, setup, and platform documentation
infra/                 Prometheus and Grafana provisioning
tests/                 Integration fixtures and load tests
dummy/                 Sample CSV datasets
```

## Build and tests

```powershell
mvn test
```

```powershell
cd frontend
npm run build
npm run lint
```

The optional k6 flow test creates real uploads and report jobs to study throughput, queue behavior, worker scaling, and bottlenecks. See [tests/load/README.md](../tests/load/README.md).

## Related documentation

- [Repository README](../README.md)
- [Setup and run instructions](../SETUP.md)
- [MVP scope](MVP-SCOPE.md)
- [MVP API reference](MVP-API.md)
- [MVP architecture](MVP-ARCHITECTURE.md)
- [MVP test plan](MVP-TEST-PLAN.md)
- [Load-test report](../tests/Project%20monitioring/LOAD-TEST-REPORT.md)
- [Scaling and optimization notes](../tests/Project%20monitioring/SCALING-500-1000-USERS.md)
- [Grafana dashboard reading guide](../tests/Project%20monitioring/GRAFANA-DASHBOARD-READING-GUIDE.md)
- [Grafana metrics reference](../tests/Project%20monitioring/GRAFANA-METRICS-GUIDE.md)

## Current demonstration flow

The working demonstration currently focuses on:

```text
CSV upload -> asynchronous monthly report job -> PDF/CSV result
```

This demonstrates the same core patterns required for a broader document platform: authenticated APIs, durable queues, asynchronous workers, idempotency, safe job claiming, object storage, result links, retries, observability, and horizontal worker scaling.

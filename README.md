# DocEngine

## Distributed Document Processing Platform

DocEngine accepts header-based CSV uploads, queues conversion jobs, renders each record into a PDF, and stores the PDF in object storage. It supports varying column names and counts rather than requiring a sales-specific schema.

The architecture can be extended for other document types, but invoice, certificate, statement, bulk-PDF, and notification workers are future extensions—not current features.

The application is structured as a distributed document-processing platform even though the current running configuration contains one API instance and one CSV-to-PDF worker instance. The API and worker can be replicated when traffic, queue depth, or document volume increases.

## What the application does

DocEngine separates request handling from document generation:

- The API accepts an authenticated request and returns quickly with a job identifier.
- Uploaded source data is stored in AWS S3 in the deployed environment (MinIO is used for local development).
- PostgreSQL stores tenants, job metadata, status, idempotency records, and processing information.
- RabbitMQ provides the durable handoff between the API and background workers.
- The CSV-to-PDF worker claims jobs, includes all input records and columns in the PDF, and stores the result in object storage.
- Clients poll job status and receive a short-lived download URL when the document is ready.
- Prometheus and Grafana provide application, JVM, HTTP, database-pool, and worker-pipeline visibility.

## End-to-end flow

```text
Client
  |
  v
Spring Boot API
  |-- authenticate tenant and validate request
  |-- store input in object storage
  |-- create job metadata in PostgreSQL
  |-- publish job message to RabbitMQ
  v
RabbitMQ queue
  |
  +--> CSV-to-PDF worker (current implementation)
              |
              v
       Object Storage (S3 / MinIO)
              |
              v
       Status update + signed download URL
```

Additional worker types can be added later using the same API-to-queue pattern.

## Supported document-processing model

Potential future job types include:

```text
INVOICE
REPORT
CERTIFICATE
STATEMENT
BULK_PDF
```

Dedicated queues and workers could be introduced for these job types. They are not currently implemented.

Typical templates include:

- Invoice template
- Monthly report template
- Certificate template
- Account statement template

## Implemented processing behavior

The current implementation includes tenant API-key authentication, upload and job endpoints, asynchronous RabbitMQ processing, idempotent job submission, atomic worker job claiming, lease/heartbeat recovery behavior, tenant-isolated job reads, and short-lived result download URLs. CSV input must be comma-delimited, have a header row, and use the same number of fields in each record. Quoted commas and multiline quoted fields are supported. PDF text currently normalizes characters outside basic ASCII, so non-Latin characters may be replaced.

The job lifecycle exposed by the application includes:

```text
QUEUED -> PROCESSING -> COMPLETED
             |
             +-> RETRYING -> PROCESSING
             |
             +-> FAILED
             |
             +-> CANCELLED
```

Priority queues, a dedicated retry/dead-letter workflow, progress percentages, scheduled bulk generation, and notification delivery are not part of the current deployed feature set.

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

When traffic increases, the API and worker can be replicated after validating database connection limits, queue behavior, storage throughput, and deployment capacity. The current deployment runs one API instance and one CSV-to-PDF worker; replicas and separate worker pools are scaling options, not active instances today.

This design avoids making the API perform expensive document generation synchronously. The API remains responsive while the queue absorbs bursts and workers process jobs at a controlled rate.

## Example future scaling scenario

For a future high-volume statement-generation workload:

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

## Deployment services and technology responsibilities

| Component | Responsibility |
|---|---|
| React frontend | Upload data, submit jobs, display status, download results |
| Spring Boot API | Authentication, validation, uploads, job creation, status APIs |
| PostgreSQL | Tenants, jobs, idempotency, status, and processing metadata |
| RabbitMQ | Durable asynchronous job delivery and workload buffering |
| Worker service | CSV parsing, PDF generation, and result storage |
| MinIO / S3 | Source files and generated document storage |
| Docker Compose | Reproducible service orchestration |
| Prometheus | Metrics collection |
| Grafana | Dashboards and operational visibility |

## Deployed application

The current deployment has been smoke-tested end to end: the Vercel frontend sends
an upload to the API, the worker processes the queued job, the generated PDF is stored
in S3, and Grafana receives API and worker metrics.

| Service | Role in the deployment |
|---|---|
| [Vercel frontend](https://frontend-three-lilac-58.vercel.app) | Hosts the React/Vite web app |
| [AWS EC2 API](https://api.jayrana.in/actuator/health) | Runs the Spring Boot API behind the public HTTPS endpoint |
| AWS EC2 + Docker Compose | Runs one API instance, one CSV-to-PDF worker, RabbitMQ, Prometheus, and Grafana |
| Supabase | Hosts PostgreSQL job and tenant data |
| Amazon S3 | Stores uploaded CSV files and generated PDFs; EC2 accesses the bucket through its instance role |

The deployed app currently runs one API instance and one worker instance. The
architecture can scale, but additional replicas and high-availability infrastructure
are not currently deployed. Generic CSV-to-PDF conversion is implemented; other
document types and specialized workers described above are future extensions.

### Private production monitoring

Prometheus and Grafana are not open to the public internet. Their EC2 ports are bound
to loopback and should remain closed in the EC2 security group. From an authorized
computer with the EC2 SSH key, create a local tunnel:

```bash
ssh -i "path/to/docengine-prod-key.pem" -N \
  -L 3000:127.0.0.1:3000 \
  -L 9090:127.0.0.1:9090 \
  ubuntu@EC2_PUBLIC_IP
```

Keep the SSH session open, then use Grafana at `http://127.0.0.1:3000` and Prometheus
targets at `http://127.0.0.1:9090/targets`. Sign in to Grafana with the private
credentials configured in EC2's `deploy/ec2/.env.production`. Do not create public
Grafana dashboard links for production metrics.

The local development stack differs: it uses PostgreSQL and MinIO containers from
`docs/docker-compose.mvp.yml`; production uses Supabase PostgreSQL and S3. Both local
and production Compose stacks include Prometheus and Grafana, but production access
is private-only.

## Current deployment shape and scaling model

The current deployment runs with one API instance and one CSV-to-PDF worker instance:

```text
Client -> API instance -> RabbitMQ -> CSV-to-PDF worker instance
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

Open `http://localhost:5173` and use the demo tenant key documented in [SETUP.md](SETUP.md).

### Verify the services

```powershell
docker compose --env-file .env -f docs/docker-compose.mvp.yml ps
curl.exe http://127.0.0.1:8081/actuator/health
```

The API health endpoint should return a healthy status. The complete upload-to-PDF flow is documented in [SETUP.md](SETUP.md).

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
dummy data/            Sample CSV datasets
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

The optional k6 flow test creates real uploads and conversion jobs to study throughput, queue behavior, worker scaling, and bottlenecks. See [tests/load/README.md](tests/load/README.md).

## Related documentation

- [Setup and run instructions](SETUP.md)
- [EC2 production Compose setup](deploy/ec2/README.md)
- [Platform overview](docs/DOCENGINE-OVERVIEW.md)
- [MIT License](LICENSE)
- [Code of Conduct](CODE_OF_CONDUCT.md)

> **Production credential reminder:** the demo flow uses a sample tenant API key. Replace it with a unique, securely managed production credential before allowing general public use. Never publish real API keys, passwords, or `.env` files.

## Current demonstration flow

The working demonstration currently focuses on:

```text
Header-based CSV upload -> asynchronous conversion job -> PDF result
```

This demonstrates the same core patterns required for a broader document platform: authenticated APIs, durable queues, asynchronous workers, idempotency, safe job claiming, object storage, result links, retries, observability, and horizontal worker scaling.

# DocEngine

DocEngine is a distributed document-processing platform. It currently demonstrates CSV upload, asynchronous job processing, and PDF/CSV report generation, while its API, queue, worker, object-storage, and monitoring architecture is designed to support multiple document-processing workloads.

The HTTP API accepts requests quickly and sends report work to RabbitMQ. The current deployment uses one API instance and one worker instance; additional instances can be added to scale request handling and document processing horizontally. MinIO stores uploaded files and generated reports. Prometheus collects API/worker metrics, which Grafana displays in a provisioned dashboard.

## Architecture

![DocEngine architecture](tests/Project%20monitioring/docengine-architecture%20.png)

## Main features

- Tenant API-key authentication for upload and job endpoints.
- CSV upload to MinIO and asynchronous monthly report jobs.
- Job status polling and short-lived result download URLs.
- Idempotent job submissions and tenant-isolated job reads.
- Atomic worker job claims and lease/heartbeat recovery behavior.
- PDF and CSV report output.
- Prometheus application/JVM/HTTP/Tomcat metrics and a provisioned Grafana overview dashboard.
- k6 real-flow load-test script for measuring throughput, queue behavior, and worker scaling.

## Quick start

For detailed setup and testing instructions, see [RUNNING-AND-TESTING.md](docs/RUNNING-AND-TESTING.md).

1. Install Docker Desktop with Compose, Java 21, Maven 3.9+, and Node.js/npm.
2. Copy `.env.example` to `.env` and adjust local-only credentials if desired.
3. From the repository root, start the application stack:

   ```powershell
   docker compose --env-file .env -f docs/docker-compose.mvp.yml up -d --build
   ```

4. Start the frontend in a separate terminal:

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

5. Open `http://localhost:5173`. The local demo API key is `demo-tenant-key`.

## Local service addresses

| Service | Local address |
|---|---|
| Frontend (Vite) | `http://localhost:5173` |
| DocEngine API | `http://127.0.0.1:8081` |
| API health | `http://127.0.0.1:8081/actuator/health` |
| API Prometheus metrics | `http://127.0.0.1:8081/actuator/prometheus` |
| Worker Prometheus metrics | `http://127.0.0.1:8082/actuator/prometheus` |
| Prometheus | `http://127.0.0.1:9090` |
| Grafana | `http://127.0.0.1:3000` |
| RabbitMQ management | `http://127.0.0.1:15672` |
| MinIO console | `http://127.0.0.1:9001` |
| pgAdmin | `http://127.0.0.1:5050` |
| PostgreSQL from host tools | `127.0.0.1:5433` |

Container-to-container traffic uses Compose service names and internal ports (for example, `postgres:5432` and `docengine-api:8080`). The host ports above are for tools running on your computer.

## Optional load testing

The k6 script creates real CSV uploads and report-job submissions. It can consume significant CPU, memory, database connections, and object storage, so start with a conservative VU count. Read [tests/load/README.md](tests/load/README.md) before running it.

## Project layout

```text
services/
  docengine-api/       Spring Boot HTTP API
  docengine-worker/    RabbitMQ background report worker
  docengine-common/    Shared domain and messaging types
frontend/             React + Vite user interface
database/migrations/  Flyway SQL migrations
docs/                 Compose file and MVP documentation
infra/                Prometheus and Grafana provisioning
tests/                Integration fixtures and optional k6 load test
dummy data/           Sample CSV datasets for manual testing
```

## Further documentation

- [DocEngine platform overview](docs/DOCENGINE-OVERVIEW.md)
- [Setup and run instructions](SETUP.md)
- [MIT License](LICENSE)
- [Code of Conduct](CODE_OF_CONDUCT.md)

# DocEngine setup guide

This guide starts DocEngine on a developer machine using Docker Desktop for the backend and local Node.js for the React frontend. Commands are written for Windows PowerShell unless marked otherwise.

## 1. Prerequisites

- Docker Desktop with Docker Compose v2, running in Linux-container mode.
- Java 21 and Maven 3.9 or newer (needed to run Maven builds/tests on the host; not required just to run the prebuilt Compose stack).
- Node.js and npm (for the frontend).
- Git (to clone the repository).

For a lower-resource computer, start with the regular app and observability stack. Do not run the optional k6 load test unless the machine has sufficient resources; it can generate many uploads/jobs and fill MinIO storage.

## 2. Get the project and configure local credentials

Clone the repository, then change into its root folder:

```powershell
git clone https://github.com/<your-account>/DocEngine.git
cd DocEngine
```

Create the local environment file from the checked-in example:

```powershell
Copy-Item .env.example .env
```

`.env` is ignored by Git. The values in `.env.example` are demo defaults only. Change the local passwords if desired, but keep the credentials consistent for services that share them. Never commit `.env` or use these defaults for a public/production deployment.

## 3. Start the backend and monitoring stack

From the repository root:

```powershell
docker compose --env-file .env -f docs/docker-compose.mvp.yml up -d --build
```

This Compose file starts PostgreSQL, RabbitMQ, MinIO, pgAdmin, the DocEngine API, the worker, Prometheus, and Grafana. The first build can take several minutes while Docker downloads base images and Maven dependencies.

Check container status:

```powershell
docker compose --env-file .env -f docs/docker-compose.mvp.yml ps
```

Wait until PostgreSQL and RabbitMQ show healthy and the API/worker are running. Check the API health endpoint:

```powershell
curl.exe http://127.0.0.1:8081/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

The API runs in the container on port 8080 and is published to host port 8081. The worker's HTTP/Actuator port is published as host port 8082. PostgreSQL is published as host port 5433, while other containers connect to it on `postgres:5432`.

## 4. Start the React frontend

Open a second PowerShell terminal in the repository root:

```powershell
cd frontend
npm install
npm run dev
```

Open the URL printed by Vite, normally `http://localhost:5173`. The frontend currently calls `http://127.0.0.1:8081` and uses `demo-tenant-key` as the pre-filled local API key. Choose a CSV, upload it, select a report month, create the job, and wait for the status to complete. The download link appears when the API provides the result URL.

If uploads fail in the browser, check that the API container is healthy and that the API CORS configuration allows `http://localhost:5173`.

## 5. Try the complete report flow

You can also exercise the API directly from PowerShell. This example uses the checked-in September fixture.

Upload the CSV:

```powershell
$upload = curl.exe -sS `
  -X POST http://127.0.0.1:8081/api/v1/uploads `
  -H "X-API-Key: demo-tenant-key" `
  -F "file=@tests/integration/resources/september-sales.csv" |
  ConvertFrom-Json

$upload
```

Submit a report job using the returned MinIO input reference:

```powershell
$payload = @{
  jobType = "GENERATE_MONTHLY_REPORT"
  input = @{
    month = "2026-09"
    dataFile = $upload.inputReference
  }
} | ConvertTo-Json -Compress

$job = Invoke-RestMethod `
  -Uri http://127.0.0.1:8081/api/v1/jobs `
  -Method Post `
  -Headers @{
    "X-API-Key" = "demo-tenant-key"
    "Idempotency-Key" = "setup-report-$(Get-Random)"
  } `
  -ContentType "application/json" `
  -Body $payload

$job
```

Poll until the job reaches a terminal status:

```powershell
do {
  $status = Invoke-RestMethod `
    -Uri "http://127.0.0.1:8081/api/v1/jobs/$($job.jobId)" `
    -Headers @{ "X-API-Key" = "demo-tenant-key" }
  $status | Select-Object jobId,status,resultUrl
  if ($status.status -notin @("COMPLETED", "FAILED")) {
    Start-Sleep -Seconds 2
  }
} while ($status.status -notin @("COMPLETED", "FAILED"))
```

When status is `COMPLETED`, download the file from the returned URL before it expires (the local default is 15 minutes):

```powershell
Invoke-WebRequest -Uri $status.resultUrl -OutFile .\docengine-monthly-report.pdf
```

The worker also creates the CSV report in the results bucket. The job's `resultUrl` points to the PDF report.

## 6. Open Prometheus and Grafana

- Prometheus targets and query UI: `http://127.0.0.1:9090/targets`
- Grafana: `http://127.0.0.1:3000`
- API metrics: `http://127.0.0.1:8081/actuator/prometheus`
- Worker metrics: `http://127.0.0.1:8082/actuator/prometheus`

The Grafana login is controlled by `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` in `.env`. The Prometheus data source and DocEngine dashboard are provisioned from files under `infra/grafana/provisioning/`.

On the Prometheus `/targets` page, the `docengine-api` and `docengine-worker` targets should be `UP`. In Grafana, open the provisioned **DocEngine — System Overview** dashboard.

## 7. Run tests

From the repository root:

```powershell
mvn test
```

The atomic-claim integration test connects to PostgreSQL directly. If using the Compose database, the defaults in the test may not match your host mapping or `.env` values. Supply the connection settings as Maven system properties; substitute your local values without sharing them:

```powershell
mvn test `
  "-Duser.timezone=Asia/Kolkata" `
  "-Ddocengine.test.jdbc-url=jdbc:postgresql://localhost:5433/docengine" `
  "-Ddocengine.test.db-user=<POSTGRES_USER>" `
  "-Ddocengine.test.db-password=<POSTGRES_PASSWORD>"
```

Build/lint the frontend:

```powershell
Set-Location frontend
npm run build
npm run lint
Set-Location ..
```

Testcontainers-based tests may require Docker Desktop to be running. The tests that connect to an already-running local PostgreSQL instance need the correct host port, username, password, and a PostgreSQL-compatible JVM timezone.

## 8. Useful commands

```powershell
# View logs
docker compose --env-file .env -f docs/docker-compose.mvp.yml logs -f docengine-api docengine-worker

# Restart services after rebuilding images
docker compose --env-file .env -f docs/docker-compose.mvp.yml up -d --build docengine-api docengine-worker

# Show current container usage
docker stats

# Stop containers but preserve named data volumes
docker compose --env-file .env -f docs/docker-compose.mvp.yml down
```

To remove database and MinIO data as well, `docker compose ... down -v` deletes the named volumes and their contents. Only do this when you intentionally want to reset local data.

## 9. Optional k6 load test

Install k6 separately, ensure the API and worker stack is running, then read [tests/load/README.md](tests/load/README.md). Begin at 100 VUs and increase gradually. Load tests create real input objects and queued jobs; clean up test objects between sessions if appropriate. They are not part of normal setup and should not be run on a constrained machine.

## Troubleshooting

- **API health is unavailable:** run `docker compose ... ps` and inspect `docker compose ... logs docengine-api`; check PostgreSQL/RabbitMQ health and wait for migrations/startup.
- **Upload or job returns 401:** use `X-API-Key: demo-tenant-key` for the local demo tenant.
- **Browser reports CORS:** confirm the frontend origin is `http://localhost:5173` and the API's allowed-origin configuration includes it.
- **Result URL does not open:** download it from the same host while it is still valid; check that MinIO is running and `MINIO_PUBLIC_ENDPOINT` in `.env` is reachable from the browser (normally `http://127.0.0.1:9000`).
- **Prometheus target is DOWN:** check the API/worker containers and the scrape targets in `infra/prometheus/prometheus.yml`. Prometheus reaches them by Compose service name, not host `localhost`.
- **Port already in use:** stop the process/container using that host port or update the host-side port mapping and corresponding local client URL. Keep the container-side ports expected by the Compose network unchanged unless updating service configuration too.

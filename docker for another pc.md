# DocEngine on another PC (8 GB RAM)

This guide is for running DocEngine on a second Windows computer for normal development and demos. Keep k6 load testing on the more powerful PC; it can create many uploads/jobs and use significant memory, CPU, and disk.

## Prerequisites

- Docker Desktop with Docker Compose v2 installed and running.
- Node.js LTS and npm for the React frontend.
- Git if you plan to clone the repository.
- Java 21 and Maven 3.9+ only if you also want to run Java builds/tests directly on Windows. Docker can build and run the API and worker without host Java/Maven.

## Copy or clone the project

Clone the repository or copy the project folder/zip to the second PC. Keep at least:

```text
services/                 API, worker, shared Java module, and Dockerfiles
frontend/                 React/Vite UI
database/                 Flyway migrations
docs/docker-compose.mvp.yml
infra/                    Prometheus/Grafana configuration (optional)
tests/integration/        test CSV fixture
dummy/                    sample CSV files
pom.xml
.env.example
```

It is safe to omit generated folders such as `**/target/`, `frontend/node_modules/`, and `frontend/dist/`; they can be rebuilt. Do not put your original `.env` with private credentials into a shared zip. Docker named volumes are not part of the project folder/zip, so this PC gets its own database and MinIO data.

## First-time setup (build and create containers)

Open PowerShell in the repository root, for example `D:\DocEngine\DocEngine`, and create this PC's local environment file:

```powershell
Copy-Item .env.example .env
```

The values in `.env.example` are local demo credentials. Keep them private, or change them in `.env` before running. Then build the Java service images and start only the core stack:

```powershell
docker compose --env-file .env -f docs/docker-compose.mvp.yml up -d --build postgres rabbitmq minio docengine-api docengine-worker
```

This is the command to use the first time on this PC. Compose creates the containers, builds the API/worker images from the source code, and downloads required images if they are not already present. First startup can take a few minutes.

pgAdmin is optional; start it only when you want a database UI:

```powershell
docker compose --env-file .env -f docs/docker-compose.mvp.yml up -d pgadmin
```

Prometheus and Grafana are also optional on an 8 GB PC. Start them only when you want to inspect metrics/dashboard:

```powershell
docker compose --env-file .env -f docs/docker-compose.mvp.yml up -d prometheus grafana
```

## Check that the backend started

```powershell
docker compose --env-file .env -f docs/docker-compose.mvp.yml ps
Invoke-RestMethod http://127.0.0.1:8081/actuator/health
```

The health response should contain:

```json
{"status":"UP"}
```

## Start the frontend

Open another PowerShell terminal:

```powershell
cd D:\DocEngine\DocEngine\frontend
npm.cmd install
npm.cmd run dev
```

Open the Vite URL, normally `http://localhost:5173`. The frontend calls the API at `http://127.0.0.1:8081` and has the local demo API key `demo-tenant-key` prefilled.

Sample CSV files are in:

```text
dummy/sales-january.csv
dummy/sales-september.csv
dummy/sales-mixed.csv
```

## Later starts: what `docker compose start` means

After the containers have already been created once, if you stopped them with `stop`, you can start those existing containers quickly:

```powershell
docker compose --env-file .env -f docs/docker-compose.mvp.yml start
```

`start` does not build images and cannot create containers that have never existed. If this is the first run, or you changed Java/Dockerfile code and need new images, use `up` instead:

```powershell
docker compose --env-file .env -f docs/docker-compose.mvp.yml up -d --build postgres rabbitmq minio docengine-api docengine-worker
```

View status and logs:

```powershell
docker compose --env-file .env -f docs/docker-compose.mvp.yml ps
docker compose --env-file .env -f docs/docker-compose.mvp.yml logs -f docengine-api
docker compose --env-file .env -f docs/docker-compose.mvp.yml logs -f docengine-worker
```

Stop containers without deleting their stored data:

```powershell
docker compose --env-file .env -f docs/docker-compose.mvp.yml stop
```

Avoid `docker compose down -v` unless you intentionally want to delete this PC's PostgreSQL and MinIO data. Avoid `docker system prune -a` unless you understand it can remove unused images and caches from other projects too.

## Local service addresses

```text
Frontend:          http://localhost:5173
DocEngine API:      http://127.0.0.1:8081
API health:        http://127.0.0.1:8081/actuator/health
PostgreSQL:        127.0.0.1:5433
RabbitMQ:          127.0.0.1:5672
RabbitMQ UI:       http://127.0.0.1:15672
MinIO API:         http://127.0.0.1:9000
MinIO console:     http://127.0.0.1:9001
pgAdmin:           http://127.0.0.1:5050 (optional container)
Prometheus:        http://127.0.0.1:9090 (optional container)
Grafana:           http://127.0.0.1:3000 (optional container)
Worker metrics:    http://127.0.0.1:8082/actuator/prometheus
```

Host ports are for programs running on Windows. Containers communicate using Compose service names and internal ports—for example, the API connects to `postgres:5432`, not host port 5433.

If you want to use the frontend from another device over the LAN, the frontend/API/MinIO public URLs and firewall access need LAN-address configuration; `127.0.0.1` always means the computer running the browser. Do not expose this demo stack directly to the public internet.

## Notes for a lower-resource computer

- Run only the core five containers for ordinary frontend/backend demos.
- Start pgAdmin, Prometheus, and Grafana only when needed.
- Do not run k6 load tests here; follow [tests/load/README.md](tests/load/README.md) on the stronger machine instead.
- Clean up test objects from MinIO only when you intentionally want to remove generated demo files. Never delete volumes just to free a small amount of memory; stopped containers release their running memory, while volumes hold persistent data.

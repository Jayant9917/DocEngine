# Running and Testing DocEngine

## Requirements

- Docker Desktop with Docker Compose
- Java 21 and Maven 3.9+
- Node.js and npm

## Start the application

From the repository root:

```powershell
Copy-Item .env.example .env
docker compose --env-file .env -f docs/docker-compose.mvp.yml up -d --build
```

Start the frontend in another terminal:

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

## Check the backend

```powershell
docker compose --env-file .env -f docs/docker-compose.mvp.yml ps
curl.exe http://127.0.0.1:8081/actuator/health
```

The API should return `{"status":"UP"}`.

## Run tests

```powershell
mvn test
```

```powershell
cd frontend
npm run build
npm run lint
```

## Load-test references

- [100-user test image](../tests/Project%20monitioring/100%20user.png)
- [200-user test image](../tests/Project%20monitioring/200%20user.png)
- [500-user scaling notes](../tests/Project%20monitioring/500%20users.md)
- [Load-test report](../tests/Project%20monitioring/LOAD-TEST-REPORT.md)
- [Scaling and optimization notes](../tests/Project%20monitioring/SCALING-500-1000-USERS.md)

These files show throughput, queue depth, worker capacity, database usage, and infrastructure bottlenecks as traffic increases.

## Useful service URLs

| Service | URL |
|---|---|
| Frontend | `http://localhost:5173` |
| API health | `http://127.0.0.1:8081/actuator/health` |
| Prometheus | `http://127.0.0.1:9090` |
| Grafana | `http://127.0.0.1:3000` |
| RabbitMQ management | `http://127.0.0.1:15672` |
| MinIO console | `http://127.0.0.1:9001` |

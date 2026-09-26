# DocEngine Load Test Report

## Purpose

This report records how the DocEngine system behaved when many virtual users
performed the real flow:

```text
Upload CSV -> receive inputReference -> submit report job
```

The test used the real API, PostgreSQL, RabbitMQ, MinIO, and worker services.
It did not use fake or empty requests.

The test script was:

```text
tests/load/k6-full-flow.js
```

Each successful iteration performed:

1. `POST /api/v1/uploads` with `september-sales.csv`.
2. Validation of the returned `inputReference`.
3. `POST /api/v1/jobs` with a unique `Idempotency-Key`.
4. Validation that the job was accepted with `202 Accepted`.
5. One second of sleep before the next iteration.

## Test environment

| Component | Local configuration |
|---|---|
| API | Docker container, host port `8081` |
| Worker | Docker container |
| PostgreSQL | Docker container, host port `5433` |
| RabbitMQ | Docker container, ports `5672` and `15672` |
| MinIO | Docker container, ports `9000` and `9001` |
| Test duration | 30 seconds |
| Test tool | Grafana k6, local execution |

## 100 virtual-user test

Command:

```powershell
k6 run -e VUS=100 -e DURATION=30s tests/load/k6-full-flow.js
```

### Results

| Metric | Result |
|---|---:|
| Virtual users | 100 |
| Duration | 30 seconds |
| Completed iterations | 3,000 |
| HTTP requests | 6,000 |
| Checks | 9,000 |
| Checks passed | 100.00% |
| Checks failed | 0.00% |
| HTTP request failures | 0.00% |
| Average request latency | 9.19 ms |
| Median request latency | 4.97 ms |
| p(90) request latency | 16.56 ms |
| p(95) request latency | 38.33 ms |
| Maximum request latency | 138.91 ms |
| Average iteration duration | 1.01 seconds |
| p(95) iteration duration | 1.07 seconds |
| Average request rate | 195.48 requests/second |
| Average iteration rate | 97.74 iterations/second |

### Interpretation

The system handled 100 concurrent virtual users cleanly. Every upload returned
`201 Created`, every input reference was present, and every job submission
returned `202 Accepted`.

The p(95) latency of 38.33 ms means that 95% of HTTP requests completed within
approximately 38 milliseconds during this run.

## 500 virtual-user test

Command:

```powershell
k6 run -e VUS=500 -e DURATION=30s tests/load/k6-full-flow.js
```

### Results

| Metric | Result |
|---|---:|
| Virtual users | 500 |
| Duration | 30 seconds |
| Completed iterations | 14,324 |
| HTTP requests | 28,351 |
| Checks | 42,675 |
| Checks passed | 98.60% |
| Checks failed | 1.39% |
| HTTP request failures | 1.04% |
| Average request latency | 31.56 ms |
| Median request latency | 5.29 ms |
| p(90) request latency | 26.13 ms |
| p(95) request latency | 69.30 ms |
| Maximum request latency | 1.71 seconds |
| Average iteration duration | 1.06 seconds |
| p(95) iteration duration | 1.13 seconds |
| Average request rate | 914.31 requests/second |
| Average iteration rate | 461.94 iterations/second |
| Successful uploads | 14,027 |
| Failed uploads | 297 |

### Failure behavior

The failed requests returned:

```text
status=0, body=null
connectex: No connection could be made because the target machine actively refused it
```

This means k6 did not receive an HTTP response. These were connection-level
failures, not normal API responses such as `400`, `401`, or `500`.

The API container did not restart:

```text
RestartCount: 0
```

The API logs showed request threads reaching names such as:

```text
http-nio-8080-exec-200
```

This indicates that the API reached approximately 200 active HTTP request
threads while many requests were waiting for MinIO upload operations. The API
was still alive, but some new connections could not be accepted temporarily.

## Container observations during the 500-user test

| Container | Observed CPU | Observed memory |
|---|---:|---:|
| API | 151.93% | 841.8 MiB |
| Worker | 83.76% | 437.6 MiB |
| PostgreSQL | 53.49% | 76.77 MiB |
| RabbitMQ | 13.20% | 201.9 MiB |
| MinIO | 128.05% | 309 MiB |
| pgAdmin | 0.02% | 250.9 MiB |

The exact values vary between runs. They are observations from Docker Desktop,
not fixed limits of the application.

## Comparison

| Metric | 100 users | 500 users | Change |
|---|---:|---:|---:|
| Completed iterations | 3,000 | 14,324 | Higher throughput at 500 users |
| HTTP requests | 6,000 | 28,351 | Higher load |
| Checks passed | 100.00% | 98.60% | Decreased under pressure |
| HTTP failures | 0.00% | 1.04% | Connection failures appeared |
| Average latency | 9.19 ms | 31.56 ms | Increased |
| p(95) latency | 38.33 ms | 69.30 ms | Increased |
| Maximum latency | 138.91 ms | 1.71 s | Large tail-latency increase |

## Conclusion

### At 100 users

DocEngine behaved cleanly. The API accepted all requests, created jobs, and
returned the correct status codes with low latency.

### At 500 users

DocEngine continued processing the majority of real requests and did not crash,
but it was not a zero-error run. Approximately 1% of upload requests failed at
the connection level, and latency increased significantly for the slowest
requests.

The main observed pressure point is the synchronous upload path:

```text
HTTP request thread -> check MinIO bucket -> upload file -> return response
```

At higher concurrency, many API threads are occupied by MinIO operations. The
next production-scaling improvements should include multiple API instances
behind a load balancer, carefully sized HTTP and database pools, MinIO client
reuse, rate limiting, and metrics/alerting.

## What this test proves

- The real upload endpoint works under concurrent load.
- The real job endpoint accepts concurrent submissions.
- Unique idempotency keys prevent accidental key collisions in the test.
- The API performs very well at 100 concurrent users on this local machine.
- The system remains mostly available at 500 users, but connection-level
  failures begin to appear.

## What this test does not prove

- It does not wait for every submitted job to reach `COMPLETED`.
- It does not prove that all generated PDFs were successfully downloaded.
- It does not measure production deployment across multiple API instances.
- It does not represent a final production capacity limit.


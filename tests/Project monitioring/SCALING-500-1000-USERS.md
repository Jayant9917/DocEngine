# DocEngine Optimization Guide: 500 to 1,000 Users

This document lists the improvements that can help DocEngine handle 500 to
1,000 concurrent users more reliably.

The current system already works correctly at the MVP level. The 100-user k6
test completed successfully with no HTTP failures. The 500-user test accepted
most requests but showed approximately 1% connection-level upload failures.

The goal of this guide is to explain what to optimize and why. It does not
mean every optimization must be implemented immediately.

## Current bottleneck

The current upload path is:

```text
Client -> API HTTP thread -> MinIO upload -> API response
```

At high concurrency, many API request threads wait for MinIO. When the API has
no free request capacity, new connections can be refused.

The previous bucket-existence check has already been removed. That was an
optimization because every upload now performs only the required object upload:

```text
API request -> MinIO putObject -> response
```

Removing the bucket check did not cause the 500-user problem. It reduced the
number of MinIO requests. The remaining pressure comes from keeping the file
upload inside the API request lifecycle.

## Recommended priority order

Implement and measure improvements in this order:

1. Add metrics so the bottleneck can be measured.
2. Tune API HTTP and connection pools.
3. Reuse and tune MinIO clients.
4. Add upload size limits and rate limiting.
5. Move to presigned direct-to-MinIO/S3 uploads.
6. Run multiple API containers behind a load balancer.
7. Tune PostgreSQL and RabbitMQ.
8. Scale workers and measure queue throughput.
9. Move local infrastructure to managed production services.

## 1. API container optimization

### 1.1 Use multiple API instances

One API container is a single capacity unit. For production, run multiple
stateless API instances:

```text
                    -> API 1
Client -> Load Balancer -> API 2
                    -> API 3
```

All instances share PostgreSQL, RabbitMQ, and MinIO/S3. This allows requests
to be distributed instead of forcing one JVM to handle every connection.

Docker Compose can run replicas, but a reverse proxy or load balancer is
needed to route traffic correctly. Do not expose multiple replicas directly on
the same host port.

### 1.2 Tune Tomcat request capacity

The default HTTP thread pool is not unlimited. Relevant settings include:

```properties
server.tomcat.threads.max=...
server.tomcat.threads.min-spare=...
server.tomcat.max-connections=...
server.tomcat.accept-count=...
```

Increasing these values can accept more concurrent requests, but it is not a
complete solution. Too many threads can move the bottleneck to PostgreSQL,
MinIO, or CPU. Tune gradually and measure p95 latency and error rate.

### 1.3 Keep API requests short

The API should authenticate, validate, persist, and queue work quickly. It
should not perform report generation or wait for workers.

The upload endpoint is still holding one HTTP request while bytes are sent to
MinIO. Presigned direct uploads are the strongest improvement for this path.

### 1.4 Reuse clients

Create shared application clients for:

- MinIO/S3.
- RabbitMQ connections.
- Database connections.

Do not construct a new network client for every request. Shared clients use
connection pools and reduce handshake overhead.

### 1.5 Add request protection

Add:

- Maximum upload size.
- Maximum request duration.
- Per-tenant rate limits.
- Global concurrency limits.
- `429 Too Many Requests` when capacity is exhausted.
- Clear timeouts instead of unlimited waiting.

Protecting the API is better than allowing overload to make every request
fail unpredictably.

## 2. MinIO and object-storage optimization

### 2.1 Keep bucket creation outside requests

This is already done. Buckets should be created during infrastructure setup or
application startup, not once per upload.

Required buckets:

```text
docengine-inputs
docengine-results
```

### 2.2 Reuse the MinIO client

Use one configured MinIO client bean with a reusable HTTP connection pool. The
API and worker should not create a new MinIO client for every object.

### 2.3 Use direct presigned uploads

Recommended scalable flow:

```text
1. Frontend -> API: request upload authorization
2. API -> Frontend: return presigned PUT URL
3. Frontend -> MinIO/S3: upload file directly
4. Frontend -> API: confirm upload and create job
```

This removes file-transfer bandwidth and waiting time from the API container.
The API remains responsible for tenant authorization and metadata.

### 2.4 Use production object storage

The current single MinIO container is suitable for local development. For
production, use:

- Amazon S3.
- Another managed S3-compatible service.
- A distributed MinIO cluster with multiple nodes and erasure coding.

Do not start several independent MinIO containers using the same local volume.
That is not a safe storage cluster and can corrupt data.

### 2.5 Configure object sizes and timeouts

For large files, use multipart uploads and sensible connection, read, write,
and call timeouts. Avoid holding large files entirely in JVM memory.

### 2.6 Add lifecycle cleanup

Configure lifecycle rules to remove:

- Old input files.
- Expired result files.
- Failed-job artifacts.

Otherwise storage grows forever even if the API is fast.

## 3. PostgreSQL optimization

### 3.1 Tune the Hikari connection pool

Spring Boot uses HikariCP. Important settings include:

```properties
spring.datasource.hikari.maximum-pool-size=...
spring.datasource.hikari.minimum-idle=...
spring.datasource.hikari.connection-timeout=...
spring.datasource.hikari.idle-timeout=...
spring.datasource.hikari.max-lifetime=...
```

Do not set the pool size to thousands. Every API and worker instance consumes
database connections. The total must remain below PostgreSQL's connection
capacity:

```text
API instances x API pool size
+ worker instances x worker pool size
< PostgreSQL usable connections
```

### 3.2 Add or verify indexes

Important access patterns should have indexes for:

- Tenant ID plus job ID.
- Idempotency key plus tenant ID.
- Job status and lease expiration.
- Queue/claim queries.

Indexes reduce database work during status polling and worker claiming.

### 3.3 Keep transactions short

Do not hold a database transaction while uploading to MinIO or generating a
PDF. Save the required state, commit, then perform external work where the
design allows it.

### 3.4 Separate polling load

Many frontend clients polling every two seconds can create substantial read
traffic. Later options include:

- Polling backoff.
- Longer intervals after `PROCESSING`.
- A webhook or server-sent event model.
- Read replicas for very large read traffic.
- Caching carefully chosen status responses.

### 3.5 Monitor database health

Track:

- Active connections.
- Waiting connections.
- Query latency.
- Lock waits.
- Transaction duration.
- Slow queries.
- Database CPU and storage I/O.

## 4. RabbitMQ optimization

### 4.1 Scale consumers

Multiple workers can consume the same queue:

```powershell
docker compose -f docs/docker-compose.mvp.yml up -d --scale docengine-worker=4
```

This improves job throughput, but only if PostgreSQL, MinIO, and CPU have
enough capacity.

### 4.2 Tune consumer prefetch

Prefetch controls how many messages a worker receives before acknowledging
previous work. Too high can make work distribution unfair; too low can reduce
throughput.

Tune it based on report duration and worker memory.

### 4.3 Add retry and dead-letter handling

For production, add:

- Limited retry attempts.
- Retry delays.
- Dead-letter exchange and queue.
- Failure reason storage.
- Monitoring for poison messages.

Do not retry a permanently invalid CSV forever.

### 4.4 Monitor queue depth

Track:

- Ready messages.
- Unacknowledged messages.
- Publish rate.
- Consume rate.
- Oldest message age.

If publish rate is greater than consume rate for a sustained period, workers
or downstream storage are under-capacity.

## 5. Worker container optimization

### 5.1 Scale workers horizontally

Workers are the natural way to increase report-processing throughput:

```text
RabbitMQ -> Worker 1
          -> Worker 2
          -> Worker 3
          -> Worker 4
```

Atomic claiming prevents two workers from processing the same job at the same
time.

### 5.2 Separate CPU and memory limits

PDF generation consumes CPU and memory. Configure realistic Docker limits and
observe behavior before adding replicas.

### 5.3 Avoid loading large files into memory

Stream input files from MinIO where possible. Process CSV rows incrementally
instead of reading an entire large file into a byte array.

### 5.4 Keep heartbeat and lease settings realistic

The lease must be long enough for normal processing, and heartbeat updates
must happen more frequently than lease expiration. Monitor heartbeat database
load when many workers process jobs simultaneously.

### 5.5 Make processing idempotent

A worker can receive a message again after a failure. Processing should be safe
to retry, and result paths should be deterministic or protected by job state.

## 6. Frontend optimization

### 6.1 Prefer direct uploads

The frontend should upload directly to MinIO/S3 using a presigned URL. This
reduces API CPU, API threads, and network traffic.

### 6.2 Reduce polling pressure

Use progressive polling intervals, for example:

```text
QUEUED:      every 2 seconds
PROCESSING:  every 3-5 seconds
Long-running jobs: exponential backoff
```

### 6.3 Avoid duplicate submissions

Disable the Create Report button while submitting and keep using an
idempotency key for safe retries.

### 6.4 Validate before upload

Reject obviously invalid files in the browser by checking:

- File extension.
- File size.
- Required CSV header.

The backend must still validate everything because browser validation is not a
security boundary.

## 7. Docker and host optimization

### 7.1 Use separate resource expectations

The 8 GB development PC should run normal MVP workflows only. Do not run
500- or 1,000-user k6 tests there.

### 7.2 Avoid unnecessary containers

pgAdmin is optional and should be stopped when not needed. Observability tools
should run on a stronger development or staging machine.

### 7.3 Use health checks and restart policies

Health checks should distinguish a process that is running from a service that
can actually accept work. Restart policies help recover from crashes but do
not solve capacity problems.

### 7.4 Keep images small

Use multi-stage Docker builds, a slim Java runtime image, and a `.dockerignore`
file. Smaller images reduce build and deployment time.

## 8. Observability required before serious load testing

Add metrics for:

- Request count by endpoint and status.
- Request latency p50, p95, and p99.
- Active HTTP requests.
- Hikari pool active and pending connections.
- MinIO upload latency and errors.
- RabbitMQ queue depth.
- Job processing duration.
- Job failure count.
- Lease expiration count.
- JVM heap, GC, CPU, and thread count.

Without these metrics, a load test only says that something failed, not which
dependency caused it.

## 9. Safe testing plan

Run tests gradually:

```powershell
k6 run -e VUS=100 -e DURATION=30s tests/load/k6-full-flow.js
k6 run -e VUS=250 -e DURATION=30s tests/load/k6-full-flow.js
k6 run -e VUS=500 -e DURATION=30s tests/load/k6-full-flow.js
k6 run -e VUS=1000 -e DURATION=30s tests/load/k6-full-flow.js
```

Record for every run:

- Checks passed percentage.
- HTTP failure percentage.
- p95 and p99 latency.
- Upload and job throughput.
- API CPU, memory, threads, and restarts.
- PostgreSQL connections and latency.
- RabbitMQ queue depth.
- MinIO latency and errors.
- Number of completed and failed jobs after the queue drains.

Do not call a test successful only because the API accepted requests. Confirm
that jobs eventually complete and that no work remains stuck in the queue.

## 10. Recommended architecture for production

```text
Frontend
   |
Load balancer / API gateway
   |
API 1 ---- API 2 ---- API 3
   |          |          |
   +----------+----------+
              |
   Managed PostgreSQL
   Managed RabbitMQ
   Managed S3/object storage
              |
        Worker pool
```

The local Docker Compose stack proves the application behavior. Production
capacity comes from independently scaling stateless APIs, workers, databases,
queues, and object storage.

## Final recommendation

For the current project, implement these first:

1. Re-test 500 users after removing bucket checks.
2. Add API and MinIO latency metrics.
3. Reuse and tune MinIO HTTP clients.
4. Add Hikari and Tomcat monitoring/tuning.
5. Implement presigned direct uploads.
6. Add multiple API replicas behind a load balancer.
7. Scale workers based on RabbitMQ queue depth.
8. Move MinIO/PostgreSQL/RabbitMQ to managed services for production.


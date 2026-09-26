# DocEngine k6 Load Test

This test runs the real DocEngine flow with 100 concurrent virtual users:

1. Upload the real `september-sales.csv` file.
2. Read the returned `inputReference`.
3. Submit a real monthly-report job using that reference.
4. Repeat for 30 seconds.

It measures API acceptance and latency. It does not wait for every report to
finish, because report processing time belongs to the worker throughput test,
while this test focuses on whether the API can accept concurrent users without
errors or duplicate idempotency keys.

## Run from the repository root

Make sure the API, worker, PostgreSQL, RabbitMQ, and MinIO are running first.

```powershell
k6 run tests/load/k6-full-flow.js
```

The default is 100 simultaneous virtual users for 30 seconds. To run 10,000
simultaneous virtual users for 30 seconds:

```powershell
k6 run -e VUS=10000 -e DURATION=30s tests/load/k6-full-flow.js
```

This is a very large local test. It can create a burst of approximately 10,000
uploads and 10,000 job submissions, depending on how many iterations each VU
completes during the duration. It may consume substantial CPU, memory, network,
PostgreSQL connections, RabbitMQ capacity, MinIO storage, and worker capacity.

Start with this safer progression:

```powershell
k6 run -e VUS=500 -e DURATION=30s tests/load/k6-full-flow.js
k6 run -e VUS=1000 -e DURATION=30s tests/load/k6-full-flow.js
k6 run -e VUS=5000 -e DURATION=30s tests/load/k6-full-flow.js
k6 run -e VUS=10000 -e DURATION=30s tests/load/k6-full-flow.js
```

Watch Docker while it runs:

```powershell
docker stats
docker compose -f docs/docker-compose.mvp.yml ps
```

Stop the test with `Ctrl+C` if the machine becomes unresponsive. This test
measures concurrent API acceptance; it does not wait for every queued report to
finish.

If the API uses another address:

```powershell
k6 run -e API_BASE_URL=http://127.0.0.1:8081 tests/load/k6-full-flow.js
```

## How to read the result

### Good result

- `checks.........................: 100.00%` or very close to it.
- Upload and job checks both pass. The API returns `201 Created` for uploads
  and `202 Accepted` for an accepted asynchronous job.
- `http_req_failed` is `0.00%` or close to zero.
- `http_req_duration` p(95) is reasonably low for the local machine.

`p(95)` means 95% of requests completed at or below that time. For example,
`p(95)=250ms` means 95 out of 100 requests took 250 milliseconds or less;
the slowest 5% took longer.

The exact latency depends on the computer, Docker resources, database, and
network. There is no universal good millisecond value for every machine, but
large spikes, timeouts, or an increasing error rate are warning signs.

### Bad result

Investigate if you see:

- Upload checks below 100% or responses other than `201`.
- Job checks below 100% or responses other than `202`.
- `http_req_failed` above zero.
- `checks` below 100%.
- Very high p(95), timeouts, connection refusals, or API/container restarts.

The script prints the HTTP status and response body for failed upload or job
submission checks, which helps identify the cause.

## Clean MinIO between demo sessions

Install and configure the MinIO client (`mc`) first, then run:

```powershell
mc alias set docengine http://127.0.0.1:9000 docengine docengine-local-password
mc rm --recursive --force docengine/docengine-inputs
mc rm --recursive --force docengine/docengine-results
```

This removes only the objects in the two DocEngine buckets. It does not remove
the buckets or Docker volumes.

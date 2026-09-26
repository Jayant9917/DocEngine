# How to Read the DocEngine Grafana Dashboard

This guide explains what each box on **DocEngine — System Overview** means. The dashboard refreshes every 5 seconds and normally displays the last 15 minutes.

## First: what the services/containers do

- **docengine-api** receives browser/client HTTP requests: CSV uploads, job submissions, and job-status checks.
- **docengine-worker** listens for queued jobs, claims them in the database, generates reports, and stores the results.
- **docengine-postgres** stores tenants, jobs, idempotency records, and job state. The dashboard currently shows each Java service's connection-pool usage, not PostgreSQL server CPU, disk, or query performance.
- **docengine-rabbitmq** carries job messages from the API to the worker. The current panels show RabbitMQ client publish/consume counters from the Java applications, not broker queue depth or RabbitMQ server health.
- **docengine-minio** stores uploaded CSV files and generated reports. Upload duration in the dashboard measures the API's instrumented MinIO upload call; it is not a full MinIO server dashboard.
- **docengine-prometheus** periodically reads metrics from the API and worker and stores their history.
- **docengine-grafana** displays those stored metrics as this dashboard.

Only the **API and worker** currently expose the application/JVM metrics scraped here. PostgreSQL, RabbitMQ, and MinIO server internals are not yet exported to Prometheus, so they do not each have their own CPU/memory/health panels.

## Bottleneck: Thread Saturation

### Tomcat Thread Pool Utilization

The two stats show the percentage of each Java service's Tomcat HTTP request threads that are busy:

```text
busy request threads / configured maximum request threads * 100
```

For example, `0.50%` means about one of 200 configured HTTP threads is busy. Green is below 60%; yellow is 60–80%; red is above 80%. A high value that remains high during load means the HTTP server has little spare capacity and new requests may wait or fail.

### Busy vs Configured Tomcat Threads

This graph compares current busy request threads with the configured thread limit, separately for the API and worker. Read its legend: labels ending in **busy** are current usage; labels ending in **max** are the configured limit. A busy line approaching its max line is a warning.

The legend colors are assigned to series by Grafana's palette. In the dashboard screenshot, the colors are:

| Series in the legend | What it represents |
|---|---|
| `docengine-api busy` | API HTTP request threads currently handling requests. |
| `docengine-worker busy` | Worker HTTP/Actuator request threads currently handling requests. These are not the worker's RabbitMQ job-processing threads. |
| `docengine-api max` | API Tomcat's configured maximum HTTP threads (commonly 200). |
| `docengine-worker max` | Worker's configured maximum HTTP threads. |

Colors can change if Grafana reassigns its palette; use the series names in the legend, not color alone, to identify a line.

### Average MinIO Upload Duration

Shows the average time in seconds spent in the API's MinIO upload operation. A rising upload time at the same time as API thread usage rises suggests uploads may be keeping request threads busy. This is a useful clue, not proof that MinIO is the only cause. It can be blank when no recent uploads have been recorded.

## API Traffic

### HTTP Request Rate

Shows approximately how many HTTP requests per second the API and worker receive. The API handles user requests; the worker also has HTTP endpoints such as health and metrics. During k6, expect the API's rate to rise. Prometheus scraping `/actuator/prometheus` also contributes a small amount of HTTP traffic.

### Average HTTP Request Latency

Shows the mean duration of HTTP requests, in milliseconds/seconds, per service. Lower is usually faster. It is **not P95**: the current services expose timer sum/count metrics but no histogram buckets. A mean can hide a few slow requests, so compare it with k6's P95 and errors.

### HTTP Responses by Status Code

Shows HTTP response rates grouped by status code, stacked together. In the screenshot, `HTTP 200` is the green series and `HTTP 404` is yellow. These colors are palette choices, not severity guarantees.

- `200`: successful requests (including regular health/metrics requests).
- `4xx`: client/request problems such as invalid input, missing credentials, or not found. Some may be expected when deliberately testing validation.
- `5xx`: server-side errors; unexpected increases during a healthy load test need investigation.

## Job Pipeline

### Job Events per Second

Each line is the recent rate of one worker job event. The screenshot's legend uses green for **Received**, yellow for **Claimed**, blue for **Claim conflicts**, and additional palette colors for **Completed** and **Failed**. Again, use names because colors are not fixed severity meanings.

- **Received**: worker received a job message from RabbitMQ.
- **Claimed**: a worker successfully claimed the job in PostgreSQL.
- **Claim conflicts**: a claim attempt updated no database row because that job was already claimed/owned. Occasional conflicts can happen during redelivery; investigate an unexpected sustained increase.
- **Completed**: worker successfully generated and stored the report.
- **Failed**: job processing ended in failure.

The rate is events per second, not the total number of jobs. The graph can be flat at zero or show no samples if no jobs were processed recently. Submitting a report job creates fresh activity.

### Average Job Processing Duration

Shows the average worker processing time in seconds, measured by the worker's processing timer. It is not a percentile and may be blank before any jobs have completed/failed since the worker started.

## JVM Resources

### JVM Heap Used vs Maximum

This shows memory used by Java objects in the JVM heap and the heap maximum for each service. In the screenshot legend:

| Series color in the screenshot | Series name | Meaning |
|---|---|---|
| Green | `docengine-api used` | Heap memory currently used by the API JVM. |
| Yellow | `docengine-worker used` | Heap memory currently used by the worker JVM. |
| Blue | `docengine-api max` | API JVM's available/configured heap maximum. |
| Orange | `docengine-worker max` | Worker JVM's available/configured heap maximum. |

The exact colors may change when Grafana reassigns its palette. A gradual climb of **used** memory toward **max**, especially with growing GC pauses or latency, may indicate memory pressure. Normal heap usage moves up and down as Java allocates and collects objects. The maximum line is a JVM heap limit, not the whole Docker memory limit.

### Live JVM Threads

Shows the number of active Java threads inside each service process. It includes HTTP, framework, and background threads. It is not the same as `tomcat_threads_busy_threads`, which counts only busy Tomcat request threads.

### GC Pause Time Rate

Shows how much time the JVM spends paused for garbage collection, averaged as a rate over a recent window. A sustained increase can contribute to slow responses. A short, tiny pause is normal.

## Database & Queue

### HikariCP Connection Pool Utilization

Shows active database connections divided by the maximum connections in each Java service's HikariCP pool. For example, 50% means half the pool is in use. A sustained value near 100% can mean application work is waiting for a database connection. This measures the API/worker connection pools, not PostgreSQL CPU or query speed.

### RabbitMQ Published vs Consumed Rate

Shows Java application's message publish/consume rates:

- **API published**: API sent job messages to RabbitMQ.
- **Worker consumed**: worker received messages from RabbitMQ.

The rates should rise when you submit jobs and the worker processes them. Zero is normal when idle. This does not show broker queue depth, unacknowledged messages, or RabbitMQ server resource use.

## Why a panel may be blank

- A timer needs recent events to calculate a rate/average: upload a CSV to populate upload duration; submit and process a job to populate job events and processing duration.
- Prometheus scrapes every 15 seconds. After creating activity, wait at least 15–30 seconds for samples to arrive.
- **No data** means no matching samples exist in the selected time range; it does not automatically mean a service is down. A visible zero means the metric exists but its measured value/rate is zero.

## Useful way to watch a load test

Keep this dashboard open while running k6. Watch thread utilization, HTTP rate/latency/status codes, upload duration, job completion/failure rates, heap usage, and database pool utilization together. The dashboard is currently for API/worker application metrics; server-level PostgreSQL, RabbitMQ, and MinIO panels require exporters/instrumentation in a later phase.

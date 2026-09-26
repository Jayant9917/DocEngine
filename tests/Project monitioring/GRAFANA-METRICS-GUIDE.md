# DocEngine Grafana Metrics Guide

This guide explains, in simple terms, what the planned **DocEngine — System Overview** dashboard measures. Prometheus collects the measurements from the API and worker; Grafana turns them into graphs and numbers.

The dashboard is split into five sections. Its default view is the last 15 minutes and it refreshes every 5 seconds. A graph shows how a value changes over time; a stat shows the latest value.

## 1. Bottleneck: Thread Saturation

This is at the top because our load testing found that the API can run short of Tomcat request threads. A request thread is a worker inside the API process that handles one incoming HTTP request at a time. If all available threads are busy, new requests may wait or connections may be refused.

### Tomcat Thread Pool Utilization

The stat is calculated as:

```text
busy Tomcat threads / configured maximum threads * 100
```

- `tomcat_threads_busy_threads`: how many request threads are busy right now.
- `tomcat_threads_config_max_threads`: the configured upper limit for that Tomcat connector (currently typically 200 for the API).
- The percentage compares the two for each service.

Green is below 60%, yellow is 60–80%, and red is above 80%. A high, sustained value means the service is nearing its thread limit. A brief spike is less concerning than a value that stays high while response times or errors rise.

### Busy vs Configured Tomcat Threads

This graph plots the busy-thread count and maximum-thread limit for the API and worker. If the busy line approaches the flat maximum line, that service has little spare request-thread capacity. The worker's Tomcat threads are for its HTTP/Actuator endpoints; they are separate from its RabbitMQ job-processing activity.

### Average MinIO Upload Duration

This estimates how many seconds an API upload spends in the instrumented MinIO upload operation:

```promql
rate(docengine_upload_duration_seconds_sum[1m]) /
rate(docengine_upload_duration_seconds_count[1m])
```

The timer records total upload time and number of uploads; dividing their rates gives the recent average. If this rises at the same time as API busy threads, MinIO upload latency may be contributing to thread pressure. This correlation is a clue, not proof by itself. The graph can show **No data** until uploads have occurred in the current API process and Prometheus has scraped the samples.

## 2. API Traffic

### HTTP Request Rate

`rate(http_server_requests_seconds_count[1m])` estimates HTTP requests per second, grouped by service. It helps show whether incoming request volume rises during a load test.

### Average HTTP Request Latency

The dashboard uses the available HTTP timer sum and count to calculate the mean request duration over a recent window. Lower is generally faster; an increase during load can indicate contention or a slow dependency.

Important: the services currently expose `http_server_requests_seconds_sum` and `_count`, but **do not expose `_bucket` histogram series**. Therefore the requested `histogram_quantile(... _bucket ...)` query cannot calculate P95 right now. This panel is an **average**, not P95. An average can hide a small number of very slow requests. To show P95 later, enable HTTP histogram buckets in the Spring/Micrometer configuration and verify that `http_server_requests_seconds_bucket` appears at `/actuator/prometheus` before changing the panel query.

### HTTP Responses by Status Code

This graph groups request rate by the `status` label (for example, 200, 400, 401, or 500) and stacks the results. A growing 4xx rate often means clients are sending invalid or unauthorized requests. A growing 5xx rate points to server-side errors. During an expected healthy test, most requests should be successful responses and unexpected 5xx responses should stay at zero.

## 3. Job Pipeline

These counters describe the background job journey. The dashboard applies `rate(...[1m])` to show the recent per-second event rate rather than the lifetime total.

- `docengine_jobs_received_total`: messages the worker has received from RabbitMQ.
- `docengine_jobs_claimed_total`: jobs successfully claimed in PostgreSQL by a worker.
- `docengine_jobs_claim_conflicts_total`: claim attempts that updated zero rows because the job was already owned/claimed.
- `docengine_jobs_completed_total`: jobs the worker completed successfully.
- `docengine_jobs_failed_total`: jobs that ended in failure.

The shared graph helps compare the stages. For example, messages received and jobs claimed should generally move together. A claim conflict can happen during duplicate delivery or competing attempts; it is not automatically a failure, but a sustained unexpected increase deserves investigation. Failed jobs should normally remain at zero during a healthy demo.

`docengine_job_processing_seconds` is a timer. The dashboard divides its recent `_sum` rate by its `_count` rate to show average time from the worker's processing measurement start through completion/failure handling. It shows no data until jobs have been processed since the worker started. This is a mean, not a percentile.

## 4. JVM Resources

Each service runs on the Java Virtual Machine (JVM). These graphs help spot memory pressure, thread growth, and garbage-collection activity.

### JVM Heap Used vs Maximum

- `jvm_memory_used_bytes{area="heap"}`: heap memory currently used by Java objects.
- `jvm_memory_max_bytes{area="heap"}`: the configured/available maximum for heap pools where a finite maximum exists.

The graph sums heap pools per service and ignores pools whose maximum is reported as `-1` (undefined). A used-memory line that keeps climbing toward the maximum, especially with slow responses or frequent GC, can signal memory pressure. Heap usage naturally rises and falls; it does not need to remain low at every instant.

### Live JVM Threads

`jvm_threads_live_threads` is the number of active Java threads in each process. It includes application, framework, and background threads—not only HTTP request threads. Use the Tomcat panels above when investigating HTTP pool saturation.

### GC Pause Time Rate

`rate(jvm_gc_pause_seconds_sum[5m])` shows how much time per second, on average, the JVM has spent pausing for garbage collection over the recent window. A rising value means more time is being spent in GC pauses; compare it with heap use and latency to see whether it may be affecting responsiveness.

## 5. Database & Queue

### HikariCP Connection Pool Utilization

Calculated as:

```text
active database connections / maximum pool connections * 100
```

- `hikaricp_connections_active`: connections currently in use by the service.
- `hikaricp_connections_max`: maximum connections allowed in that service's HikariCP pool.

If utilization stays near 100%, application threads may have to wait for a database connection. That is different from Tomcat thread saturation: this graph is about database connections, not HTTP threads.

### RabbitMQ Published vs Consumed Rate

- `rabbitmq_published_total`: messages published by the service to RabbitMQ.
- `rabbitmq_consumed_total`: messages consumed by the service from RabbitMQ.

The graph shows recent per-second rates, grouped by service. The API is expected to publish job messages; the worker is expected to consume them. Rates may be zero when no jobs are being submitted. These are application-client metrics, not a complete view of RabbitMQ server health, queue depth, or broker resource usage; those would require a RabbitMQ exporter in a later phase.

## Understanding “No data” and zero

**Zero** means Prometheus has a series and its current value/rate is zero. **No data** means there is no matching sample for the query/time window—for example, no job has been processed since the worker restarted, or a rate window does not yet contain enough counter samples. It does not necessarily mean the service is broken.

To create fresh samples, submit an upload and report job through the application, then allow at least one or two 15-second Prometheus scrape intervals. A failed-job line needs a real failed job; do not deliberately damage data just to populate a demo graph. RabbitMQ rates need job submissions/consumption. Upload duration needs a new file upload.

## Metric scope

This dashboard reads metrics exposed by the DocEngine API and worker. It does not yet show PostgreSQL server, RabbitMQ broker, or MinIO server internals. Those need their own exporters/instrumentation and are separate from this application-metrics dashboard.

The natural next step is to run the k6 load tests at increasing user counts (100, 150, 200, then higher) while watching thread utilization, upload duration, request rate/latency, JVM resources, and connection-pool utilization. Do not infer safe capacity from a single metric: observe errors, latency, saturation, and completed job flow together.

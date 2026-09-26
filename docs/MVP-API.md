# DocEngine MVP API

Base URL for local Windows development: `http://127.0.0.1:8081/api/v1`

Inside Docker, the API listens on port `8080`; Docker publishes it as host port `8081`. PostgreSQL similarly remains `5432` inside Docker but is published as host port `5433`.

Authentication uses the `X-API-Key` header. The server resolves the tenant from the key; clients never send `tenant_id`.

## Upload CSV

```http
POST /api/v1/uploads
X-API-Key: demo-tenant-key
Content-Type: multipart/form-data
```

Form field: `file`.

Response `201 Created`:

```json
{
  "uploadId": "uuid",
  "inputReference": "s3://docengine-inputs/tenant-id/upload-id/september-sales.csv",
  "fileName": "september-sales.csv"
}
```

The CSV must contain `orderId,customer,amount,date`. Dates use ISO format (`YYYY-MM-DD`); amounts use decimal notation. The input object is private and tenant-scoped.

## Submit a job

```http
POST /api/v1/jobs
X-API-Key: demo-tenant-key
Idempotency-Key: september-report-2026-09
Content-Type: application/json
```

```json
{
  "jobType": "GENERATE_MONTHLY_REPORT",
  "input": {
    "month": "2026-09",
    "dataFile": "s3://docengine-inputs/tenant-id/upload-id/september-sales.csv"
  }
}
```

Response `202 Accepted`:

```json
{
  "jobId": "uuid",
  "status": "QUEUED"
}
```

The API saves the job before publishing a message. The idempotency key is unique per tenant.

## Get job status

```http
GET /api/v1/jobs/{jobId}
X-API-Key: demo-tenant-key
```

Queued response:

```json
{
  "jobId": "uuid",
  "jobType": "GENERATE_MONTHLY_REPORT",
  "status": "PROCESSING"
}
```

Completed response:

```json
{
  "jobId": "uuid",
  "jobType": "GENERATE_MONTHLY_REPORT",
  "status": "COMPLETED",
  "resultUrl": "short-lived-presigned-url",
  "resultUrlExpiresAt": "2026-09-21T12:15:00Z"
}
```

The result URL is returned only for `COMPLETED` jobs, is short-lived, and is scoped to the result object. A client must request the status endpoint again after expiry.

## Health

```http
GET /actuator/health
```

## Errors

| Status | Meaning |
|---:|---|
| `400` | Invalid JSON, CSV, month, job type, or missing file |
| `401` | Missing or invalid API key |
| `404` | Job or upload is not visible to this tenant |
| `409` | Idempotency key reused with a different request body |
| `413` | Input file exceeds the configured MVP limit |
| `500` | Unexpected server failure |

## Idempotency behavior

| Request | Result |
|---|---|
| New key for tenant | Creates one job and returns `202` |
| Same key and same request hash | Returns the existing job ID/status; no new job |
| Same key and different request hash | Returns `409 Conflict` |
| Same key used by another tenant | Independent key; tenant isolation applies |

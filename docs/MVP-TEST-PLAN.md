# DocEngine MVP Test Plan

Tests are ordered by importance. The atomic-claim concurrency test must not be skipped.

## P0 — mandatory correctness tests

1. **Atomic claim race**: create one `QUEUED` job, start two worker attempts concurrently, and assert exactly one updates the row to `PROCESSING`.
2. **Idempotent submit**: submit the same tenant/request and idempotency key twice; assert one job ID and one database job.
3. **Hash conflict**: reuse an idempotency key with a different request; assert `409 Conflict`.
4. **Tenant isolation**: authenticate as tenant B and verify tenant A's job, upload, and result cannot be read.
5. **Upload and report E2E**: upload CSV, submit job, poll until `COMPLETED`, and download the result URL.

## P1 — worker and API behavior

- Reject missing/invalid API keys.
- Reject missing `Idempotency-Key`.
- Reject unsupported job types and malformed CSV columns.
- Filter input rows by the requested month.
- Store inputs and outputs under tenant-scoped MinIO paths.
- Return a result URL only after the result is durably stored.
- Return `404` for an unknown job or a job belonging to another tenant.
- Verify Flyway creates the expected schema on an empty database.

## P1 — crash/redelivery simulation

1. Publish or submit a job.
2. Stop the worker after it receives the message but before the final database update/ACK.
3. Start a second worker and allow redelivery.
4. Verify the second worker can claim only when the database state permits it, and that the report is not executed concurrently by two workers.

The MVP does not yet implement lease/heartbeat recovery, so document the exact crash point and expected behavior in the test result.

## Manual E2E checklist

- [ ] Start the five MVP services.
- [ ] Health endpoint returns `UP`.
- [ ] Generate or locate `september-sales.csv`.
- [ ] Upload the CSV and receive `inputReference`.
- [ ] Submit `GENERATE_MONTHLY_REPORT` with `Idempotency-Key`.
- [ ] Receive `202` and a job ID.
- [ ] Observe `QUEUED` → `PROCESSING` → `COMPLETED`.
- [ ] Download the generated PDF/CSV from `resultUrl`.
- [ ] Repeat the same submit request and receive the same job ID.
- [ ] Run the two-worker claim check.
- [ ] Verify a second tenant cannot access the first tenant's data.

## Evidence to keep

Keep the successful test output, API responses, worker logs showing the claim, and the generated report. This is the proof that the MVP design works before adding replicas, Redis, observability, retries, or Kubernetes.

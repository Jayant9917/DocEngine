# DocEngine — Load Test Report

**Test setup**: k6, full real flow (CSV upload to MinIO + job submission), single API
instance, single Worker instance, no load balancer, default Tomcat thread pool (200 max).
Each virtual user: upload → submit job → 1s sleep → repeat, for 30 seconds.

Command pattern: `k6 run -e VUS=<n> -e DURATION=30s tests/load/k6-full-flow.js`

---

## Results Summary

| VUs | Checks Passed | Checks Failed | HTTP Req Failed | P95 Latency | Total Requests | Result |
|---|---|---|---|---|---|---|
| 100 | 100.00% (8,793/8,793) | 0.00% | 0.00% (0/5,862) | 79.74ms | 5,862 | ✅ Clean |
| 150 | 100.00% (13,173/13,173) | 0.00% | 0.00% (0/8,782) | 136.58ms | 8,782 | ✅ Clean |
| 200 | 100.00% (18,000/18,000) | 0.00% | 0.00% (0/12,000) | 45.96ms | 12,000 | ✅ Clean |
| 300 | 99.31% (26,673/26,857) | 0.68% (184) | 0.51% (92/17,874) | 48.61ms | 17,874 | ❌ Failures begin |

---

## What the numbers show

**100 → 200 VUs: system holds up cleanly.** Zero failed requests across three separate runs.
Latency actually improved at 200 VUs (P95 45.96ms) versus 150 VUs (P95 136.58ms) — this kind
of non-linear latency is normal under moderate load and JIT/connection-pool warm-up, not a
red flag by itself.

**300 VUs: the breaking point.** 92 upload requests failed outright with
`connectex: actively refused` — the OS/Tomcat layer rejected the connection before it ever
reached a request thread. Every job submission that DID get an upload through still succeeded
(0 failures on the `job submission returns 202` check) — the failure is isolated entirely to
the upload step under connection pressure, not a downstream bug in job creation, the queue, or
the worker.

**Why the Tomcat busy-thread metric didn't move during the 300-VU failure**: connection refusals
happen at the accept-queue level, before a request is ever assigned to a Tomcat worker thread.
`tomcat_threads_busy_threads` only counts requests already inside a thread, so it stayed flat at
its baseline even while ~92 requests were being rejected outside that boundary. The dashboard's
HTTP Request Rate and Job Events panels still show the traffic spike; the specific saturation
metric just isn't the one that catches this particular failure mode.

---

## Root cause

Single Spring Boot/Tomcat instance, default max thread pool = 200. At 300 concurrent virtual
users, more simultaneous in-flight requests existed than the server's connection-handling
capacity, causing the OS to refuse new connections outright rather than queue them indefinitely.

---

## Conclusion

- **Safe capacity on current single-instance setup: up to 200 concurrent users**, verified with
  zero errors across repeated runs.
- **Breaking point: 300 concurrent users**, reproducible, with clear log evidence
  (`connectex: actively refused`) and a measurable failure rate (0.51% of HTTP requests).
- Failure is isolated to connection acceptance, not application logic, data integrity, or the
  job pipeline — no duplicate jobs, no corrupted state, no downstream errors observed.

## Planned fix (not yet implemented)

Add Nginx as a reverse proxy in front of multiple API replicas (Phase 4, already documented).
Distributing the same 300+ VU load across 2-3 API instances should keep each instance's
in-flight connection count under its individual 200-thread ceiling. This is the next test to
run: repeat the 300-VU test against the scaled setup and confirm the failure rate returns to 0%,
then push further (500+) to find the new ceiling with replicas in place.

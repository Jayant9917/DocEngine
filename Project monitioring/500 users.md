The logs reveal an important detail:
http-nio-8080-exec-200
The API reached approximately 200 concurrent request threads. That is the default Spring Boot/Tomcat worker-thread limit.
Your container did not crash:
RestartCount: 0
So the API was not killed or restarted. Instead, under 500 virtual users, the API temporarily became unable to accept new connections. That caused k6 errors:
connectex: actively refused
status=0
The API logs show many requests simultaneously doing:
Checking MinIO bucket
Uploading ... to MinIO
The current upload flow performs MinIO work inside the HTTP request. With 500 users, the API’s request threads become occupied waiting for MinIO, and new requests can be refused.
Current conclusion:
API crash:              No
API restart:            No
Memory exhaustion:      No evidence
Request-thread saturation: Yes
MinIO work under load:  Yes
The test result is therefore a real scalability finding, not a test-script error.
The next improvement should be to tune and measure:
- Tomcat request-thread limit.
- Tomcat connection and accept queue.
- PostgreSQL connection pool.
- MinIO client concurrency.
- API container CPU allocation.
- Multiple API containers behind a load balancer.
However, simply increasing threads is not always the best fix. More threads can move the bottleneck to PostgreSQL or MinIO. The production solution is usually multiple stateless API instances plus a reverse proxy/load balancer, with properly sized database and storage connection pools.


============================================================================================================
Removed bucket-existence checks from both upload paths:
- API input uploads.
- Worker-generated result uploads.
Now each upload directly performs:
putObject(...)
The API no longer performs this expensive operation for every request:
bucketExists(...)
This reduces MinIO calls and frees API request threads under high concurrency.
Important: the buckets must already exist. Your current buckets are already created:
docengine-inputs
docengine-results
The local Maven compile could not run because Maven was offline and the Spring Boot parent POM was not available in the local cache:
Cannot access central in offline mode
On your machine, run:
& "C:\Maven\apache-maven-3.9.16\bin\mvn.cmd" `
  -pl services/docengine-api,services/docengine-worker `
  -am compile
Then rebuild both Docker images:
docker compose --env-file .env -f docs/docker-compose.mvp.yml build docengine-api docengine-worker
docker compose --env-file .env -f docs/docker-compose.mvp.yml up -d --force-recreate docengine-api docengine-worker
After that, rerun the 500-user test and compare the failure rate and p95 latency.

  Edited 2 files+0-13Review changesUndoReview

storage/MinioObjectStorage.javastorage/MinioObjectStorage.java+0-8
storage/WorkerObjectStorage.javastorage/WorkerObjectStorage.java+0-5


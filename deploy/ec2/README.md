# DocEngine on EC2 (production Compose)

This Compose file runs the API, one report worker, and private RabbitMQ. PostgreSQL
is hosted by Supabase and documents use the existing private S3 bucket via the EC2
instance profile. It does not start local PostgreSQL, MinIO, pgAdmin, Prometheus, or
Grafana. The local development Compose setup is unchanged.

## Before starting

- Attach `DocEngineEC2Role` to the EC2 instance and confirm it has access to the
  existing bucket under `inputs/*` and `outputs/*`. Do not create AWS access keys.
- In Supabase, obtain a PostgreSQL Session Pooler connection or a reachable direct
  connection. Put `sslmode=require` in the JDBC URL. Fill in the exact host,
  username, and database password in the production environment file. Ensure any
  Supabase network restrictions allow this EC2 instance to connect.
- Keep the EC2 security group limited to SSH for now. RabbitMQ has no published
  ports, and the API is bound to `127.0.0.1`, not the public interface.

## Configure and start

From the repository root on EC2:

```bash
cp deploy/ec2/.env.production.example deploy/ec2/.env.production
chmod 600 deploy/ec2/.env.production
nano deploy/ec2/.env.production
docker compose --env-file deploy/ec2/.env.production -f compose.production.yaml config -q
docker compose --env-file deploy/ec2/.env.production -f compose.production.yaml up -d --build
docker compose --env-file deploy/ec2/.env.production -f compose.production.yaml ps
```

Replace all example Supabase connection values and the RabbitMQ password before
starting. Keep `.env.production` on EC2 only; it is ignored by Git. The AWS SDK
inside both application containers uses its default credential chain and the
instance role—there are deliberately no AWS key variables in Compose.

## Check health over SSH

The API listens on EC2 loopback port 8081. From your own computer, create a tunnel:

```bash
ssh -i "path/to/docengine-prod-key.pem" -L 8081:127.0.0.1:8081 ubuntu@EC2_PUBLIC_IP
```

In another local terminal:

```bash
curl http://127.0.0.1:8081/actuator/health
```

Review logs if a service is unhealthy:

```bash
docker compose --env-file deploy/ec2/.env.production -f compose.production.yaml logs --tail=100 docengine-api docengine-worker rabbitmq
```

The current worker expects the sales CSV header `orderId,customer,amount,date`.
After the API is healthy, the end-to-end verification is to upload a matching CSV,
submit a monthly report job, wait for completion, and download the result. S3 keys
created by the current implementation are under `inputs/{tenantId}/{uploadId}/...`
and `outputs/tenants/{tenantId}/jobs/{jobId}/results/`. These differ from the
initial proposed example key names but remain within the IAM policy's allowed
`inputs/*` and `outputs/*` resources. Verify known object keys with GetObject or
the returned download URL; the role intentionally has no ListBucket/DeleteObject.

This file does not configure NGINX, HTTPS, frontend hosting, or monitoring yet.

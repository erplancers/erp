# Erplancers ERP — Production UI & Docker

## Stack

- Spring Boot ERP API
- PostgreSQL 17
- React + TypeScript + Vite
- PrimeReact + PrimeIcons
- TanStack Query
- Recharts
- Nginx reverse proxy
- Apache Kafka 4 (KRaft, single-node development deployment)
- PostgreSQL transactional outbox for analytics-job notifications
- Docker Compose

The React application uses the existing `/api` REST surface, including resource browsing, analytics, BI and OLAP endpoints. Nginx keeps the browser same-origin with Spring Boot, so no production CORS configuration is required.

## Run with Docker Compose

Copy the environment template:

```bash
cp .env.example .env
```

Set a strong PostgreSQL password in `.env`, then:

```bash
docker compose up -d --build
```

Open `http://localhost:8088`.

Useful commands:

```bash
docker compose ps
docker compose logs -f backend
docker compose logs -f frontend
docker compose down
```

PostgreSQL data is persisted in the `postgres_data` volume and Kafka data in `kafka_data`.

## Kafka analytics integration

The ERP inserts each analytics job and its erp.analytics.job.queued.v1 outbox event in the same database transaction. A scheduled relay publishes pending outbox events with Kafka producer idempotence and acks=all; failed sends remain in PostgreSQL for retry. Delivery is at-least-once, so events carry an eventId and consumers must tolerate duplicates. Kafka events are wake-up notifications only: the Spark worker reloads job state and tenant-safe parameters from the PostgreSQL queue, whose row leases remain authoritative. The worker continues polling PostgreSQL if Kafka is offline.

The broker is reachable from containers at kafka:29092 and from host tools at localhost:9092. This Compose broker is a single-node development setup, not a production HA cluster.

## Embedded Spark analytics engine

Spark SQL and Spark MLlib are now packaged as a Gradle dependency of the main Spring Boot backend and run in the same backend JVM on a managed lifecycle thread. HTTP requests only enqueue jobs; they never execute Spark work on request threads. The standalone `analytics-worker` Compose service has been removed. PostgreSQL leases still coordinate job claims, while Kafka topic `erp.analytics.job.queued.v1` wakes the embedded engine promptly; database polling remains the fallback.

The engine is disabled by default. Configure an operator-approved catalogue and a separate least-privilege PostgreSQL read-only login before enabling it:

```sh
cp deploy/analytics-datasets.properties.example deploy/analytics-datasets.properties
# Edit the file: replace every YOUR_ placeholder with real approved views/columns.
# In .env set ANALYTICS_ENGINE_ENABLED=true and configure ANALYTICS_READ_USER/PASSWORD.
docker compose up -d --build
docker compose logs -f backend
```

The backend mounts `deploy/` read-only at `/run/config` and persists output under the `analytics_artifacts` volume. Do not enable the engine with placeholder mappings. For production, configure distinct least-privilege metadata/read-only PostgreSQL roles, protected secrets, private artifact storage, resource limits, and a multi-node secured Kafka deployment. Spark now shares backend JVM memory and CPU, so set container memory/CPU limits and monitor heap, GC, and job concurrency.


## Production notes

- Put the Compose stack behind HTTPS at the customer edge/load balancer.
- Use a long random `POSTGRES_PASSWORD`; do not commit `.env`.
- The UI retains Basic-auth credentials only in `sessionStorage` because the current backend exposes stateless HTTP Basic authentication. HTTPS is therefore required in production.
- The backend currently uses Hibernate `update` for first-deployment schema management. Once database migrations are introduced, switch production to `validate` and manage schema changes through migrations.
- The frontend container serves immutable static assets and proxies `/api/` to the backend.

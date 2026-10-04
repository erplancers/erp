# Erplancers ERP — Production UI & Docker

## Stack

- Spring Boot ERP API
- PostgreSQL 17
- React + TypeScript + Vite
- PrimeReact + PrimeIcons
- TanStack Query
- Recharts
- Nginx reverse proxy
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

PostgreSQL data is persisted in the `postgres_data` volume.

## Production notes

- Put the Compose stack behind HTTPS at the customer edge/load balancer.
- Use a long random `POSTGRES_PASSWORD`; do not commit `.env`.
- The UI retains Basic-auth credentials only in `sessionStorage` because the current backend exposes stateless HTTP Basic authentication. HTTPS is therefore required in production.
- The backend currently uses Hibernate `update` for first-deployment schema management. Once database migrations are introduced, switch production to `validate` and manage schema changes through migrations.
- The frontend container serves immutable static assets and proxies `/api/` to the backend.

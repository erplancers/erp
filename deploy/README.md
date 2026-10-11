# Embedded analytics deployment configuration

The backend mounts this directory read-only at `/run/config`.

Before enabling the embedded Spark engine:

1. Copy `analytics-datasets.properties.example` to `analytics-datasets.properties`.
2. Replace every `YOUR_...` placeholder with verified physical tables/views and columns from the deployed ERP schema.
3. Use approved database views, tenant keys, and scope keys. Never use the API to select physical tables or columns.
4. Configure a distinct PostgreSQL read-only login for `ANALYTICS_READ_USER` / `ANALYTICS_READ_PASSWORD`, with SELECT only on the approved views and only the required grant tables.
5. Set `ANALYTICS_ENGINE_ENABLED=true` in `.env`, then recreate the backend.

```sh
cp deploy/analytics-datasets.properties.example deploy/analytics-datasets.properties
# Edit deploy/analytics-datasets.properties before enabling the engine.
docker compose up -d --build
docker compose logs -f backend
```

Do not commit `analytics-datasets.properties` if it contains deployment-specific schema details. The engine stays disabled by default until a real catalogue and database grants are configured.

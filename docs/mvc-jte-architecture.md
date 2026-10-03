# ERP MVC + JTE architecture

This branch adds a registry-driven MVC layer over every Spring Data repository extending `ErpRepository`.

## Layers

- **Repository**: existing domain-specific `*Repository` interfaces remain the persistence/query layer.
- **Service**: `DynamicCrudService` provides transactional CRUD, pagination, simple-property conversion, and metadata discovery.
- **Controller**: `CrudController` exposes the server-rendered application under `/app`.
- **View**: JTE templates under `src/main/resources/templates/crud` provide module index, list, detail, and create/edit forms.
- **Registry**: `CrudRegistry` discovers all repository beans after application initialization and maps their domain types to stable URL slugs.

## Routes

- `GET /app` — all discovered ERP resources.
- `GET /app/{resource}` — paginated list.
- `GET /app/{resource}/{id}` — detail.
- `GET /app/{resource}/new` — create form.
- `POST /app/{resource}/save` — create/update.
- `POST /app/{resource}/{id}/delete` — delete.

Existing domain APIs can coexist with this UI layer. The Account API was completed and now exposes:

- `GET /api/accounts`
- `GET /api/accounts/{id}`
- `GET /api/accounts/{id}/balance`

## Scope

The current repository contains 63 domain repositories across accounting, communication, content, CRM, events, finance, inventory, and platform. They are covered automatically by the registry rather than by duplicating controller/service classes for every entity.

The generic form intentionally edits only scalar properties. Entity relationships should continue to use domain-specific application services/forms where validation, authorization, or aggregate rules are required.

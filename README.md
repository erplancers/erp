# erp

## BI workspace and scheduled analytics

The BI workspace exposes tenant-scoped saved reports and calculated measures under `/api/bi-workspace`.
Calculated measures use a deliberately limited expression language (arithmetic, `ratio`, `margin`,
`periodChange`, `periodChangePct`, `abs`, `min`, `max`, and ordered `runningTotal(field)`);
expressions are parsed by the application and are never executed as SQL, SpEL, or JavaScript.

Saved report definitions can be exported as CSV, XLSX, or PDF. Report schedules create durable delivery records;
to enable the built-in SMTP worker, configure Spring's `spring.mail.*` settings and set
`bi.delivery.enabled=true`. Optional settings are `bi.delivery.from` (default `noreply@localhost`)
and `bi.delivery.worker-delay-ms` (default 30000). The worker rechecks the report owner's tenant grant
and resource READ policy before generating or sending an attachment. Keep the mail relay restricted and
use authenticated TLS in production.

The `/api/bi-workspace/monitoring` endpoint reports tenant-scoped queue, cache, refresh-state, drift, and
recent-operation information. Saved report results are cached for two minutes per tenant/user/resource/definition.
The current JPA BI query path remains live-query based; `bi_refresh_state` records watermarks for future
materialized or incremental dataset refresh workers, but does not itself implement incremental extraction.

Scheduled ML jobs are managed under `/api/analytics/schedules`. They enqueue durable Spark jobs rather than
running Spark in HTTP requests. The scheduler revalidates tenant and row-scope grants, and the Spark reader
rechecks those grants at extraction time. Training jobs persist model versions and available evaluation metrics;
daily drift checks and candidate review/promotion remain part of the existing model governance lifecycle.

# ERP Business Intelligence and ML Platform

The repository contains two separately deployed pieces:

- **Spring Boot control plane** — authenticated job submission/status/cancellation, tenant grants, experiment/model registry APIs, audited model promotion/rollback, and daily drift scheduling.
- **Spark worker** — a separate JVM process running Spark SQL and Spark MLlib jobs. It reads from explicitly allowlisted PostgreSQL views with tenant/entity-scope filtering applied in PostgreSQL, then stores artifacts and Parquet results in protected storage.

Spark does not execute inside ordinary ERP CRUD HTTP requests. The current implementation is a production-oriented foundation; it is not a substitute for staging, threat-model review, real-schema mapping, performance/load testing, and operational runbooks before a production rollout.

## Included analytics

| Job type | Dataset key | What it produces |
| --- | --- | --- |
| `SALES_TREND_ANALYSIS` | `sales_orders` | Calendar-complete monthly revenue and order counts, average order value, month-over-month change/growth, trailing three-month average, and year-over-year growth |
| `RFM` | `sales_orders` | Recency, frequency, monetary value and historical/annualized revenue measures |
| `COHORT_ANALYSIS` | `sales_orders` | Cohort month × age-month retention counts |
| `CUSTOMER_LIFETIME_VALUE` | `sales_orders` | Historical revenue and annualized observed revenue estimate; **not** a discounted predictive CLV |
| `CUSTOMER_SEGMENTATION` | `sales_orders` | Scaled K-means segments, silhouette metric, saved pipeline, and predictions |
| `CUSTOMER_CHURN_RISK` | `sales_orders` | Time-ordered churn/retention propensity using logistic regression, random forest, or gradient-boosted trees; AUC/precision/recall and a human-review queue |
| `DEMAND_FORECAST` | `inventory_daily` | Lag-feature regression with an out-of-time validation set and RMSE/MAE/R² |
| `STOCKOUT_RISK` | `inventory_daily` | Time-ordered labelled logistic regression and AUC/precision/recall |
| `INVENTORY_ANOMALY` | `inventory_daily` | Rolling 28-observation demand anomalies using z-scores |
| `MARKET_BASKET` | `order_lines` | FP-Growth frequent itemsets and association rules |
| `PRODUCT_RECOMMENDATIONS` | `order_lines` | Spark MLlib implicit-feedback ALS recommendations for products within a transaction/basket context; model and ranked recommendation output |
| `RECEIVABLES_RISK` | `receivables` | Time-split overdue-risk scoring, holdout metrics, and a human-review queue only |
| `DATA_DRIFT` | Same dataset as the trained model | PSI per feature against a saved baseline; scheduled daily for eligible production models |
| `MODEL_SCORING` | Same dataset as the trained model | Scores current rows with a specified, promoted pipeline version; writes tenant-scoped Parquet results |

### Model-quality guardrails

- Train/validation split is time-based for demand forecasting, stock-out risk, and receivables risk to reduce temporal leakage. Historical data must have an honest business date and label definition.
- Receivables output is decision support only. It emits `HUMAN_REVIEW_ONLY`; it must not automatically reject customers, change payment terms, or block orders.
- Churn propensity output is decision support only. It emits `RETENTION_REVIEW_ONLY`; it must not automatically change pricing, terminate accounts, or trigger customer communications.
- `CUSTOMER_CHURN_RISK` requires the operator-managed `sales_orders` view to expose a meaningful binary `churn_label` and numeric feature aliases. The label must be defined at a real decision date; exclude post-decision fields and future outcomes from features. Train/holdout separation is based on `order_date`, and at least 50 training and 10 later-date holdout rows are required.
- Historical annualized revenue is explicitly not presented as predictive CLV.
- PSI's 0.25 alert default is an operational heuristic, not universal statistical proof of drift. Calibrate it against feature distributions and business impact.
- An artifact is first registered as `CANDIDATE`. A separately authorized reviewer must validate it before promotion. Promotion retires the previous production version in the same DB transaction. Rollback promotes a previously retired version and writes an audit record.
- Promotion is a control-plane state transition. The consumer application must resolve the current model version from the registry rather than from an unversioned filesystem path.

## Build and test

The Spring Boot application uses its normal configured JDK toolchain; the Spark subproject targets Java 17 (Spark 4 runtime baseline). CI runs both ERP and Spark builds.

```sh
./gradlew test bootJar --no-daemon
./gradlew :spark-analytics:test :spark-analytics:build --no-daemon
```

For a local Spark smoke run use `SPARK_MASTER=local[*]`. Use a real PostgreSQL staging database for the worker queue and extraction checks. Spark workers must be packaged and deployed separately from the frontend/backend image.

## Schema migration and grants

The Flyway migration `src/main/resources/db/migration/V2026101001__analytics_bi_ml_platform.sql` creates the job queue, tenant/scope grants, experiment log, model versions, review audit, and drift metrics.

Provision grants through a trusted DBA/administrative workflow, not an end-user API. Example only:

```sql
INSERT INTO analytics_tenant_grant (username, tenant_id, active)
VALUES ('alice', 'tenant-a', TRUE);

INSERT INTO analytics_scope_grant (username, tenant_id, scope_type, scope_id, active)
VALUES ('alice', 'tenant-a', 'CONTACT', 'contact-42', TRUE);

-- For a warehouse-scoped dataset, grant a WAREHOUSE scope instead:
INSERT INTO analytics_scope_grant (username, tenant_id, scope_type, scope_id, active)
VALUES ('alice', 'tenant-a', 'WAREHOUSE', 'warehouse-7', TRUE);
```

Names and IDs must match actual identities and keys in your deployment. Grant only the rows that the user is authorized to analyse. Configure ERP policy rules for resource `analytics` with the necessary actions:
- `READ`, `WRITE` — query reports, submit/cancel jobs.
- `MODEL_APPROVE` — validate/reject candidate models.
- `MODEL_PROMOTE` — promote validated candidates.
- `MODEL_ROLLBACK` — return to a previously retired version.

Do not grant model lifecycle actions to ordinary report users.

## PostgreSQL roles and least privilege

Use different credentials for the metadata/control plane and the row-extraction worker:

1. `ANALYTICS_METADATA_USER` needs the limited INSERT/UPDATE/SELECT permissions required on analytics metadata tables. It is not the source-data extraction account.
2. `ANALYTICS_READ_USER` must be a read-only account. Grant `SELECT` only on approved views and read access to `analytics_tenant_grant` and `analytics_scope_grant`; do not grant write access to ERP tables. The worker sets each Spark JDBC session to read-only.
3. Network rules should allow the worker to reach PostgreSQL and the protected artifact store only. Do not expose Spark master, driver or Spark UI ports publicly.
4. Use a dedicated secret manager in deployment; never commit passwords or filled-in production dataset configuration to this repository. For S3A/ABFS, configure workload identity/credential providers and endpoint settings through protected Hadoop configuration; do not hard-code cloud secrets in code or the example properties.

The metadata schema migration shown here creates tables; it does **not** create DB roles, grants or production entity mappings for you. Validate these grants with a non-admin account before production.

## Configure the worker

Copy `src/main/resources/analytics-datasets.example.properties` to a protected deployment location and change every `YOUR_...` placeholder to approved source views and columns. The catalogue rejects placeholders and unsafe SQL identifiers. Aliases must match the logical columns used in each job. All source tables and projections are fixed by the operator-managed catalogue; API callers cannot pass SQL or physical table names.

Available environment variables:

| Variable | Purpose |
| --- | --- |
| `ANALYTICS_METADATA_JDBC_URL` | PostgreSQL URL for analytics metadata/job queue |
| `ANALYTICS_METADATA_USER`, `ANALYTICS_METADATA_PASSWORD` | Metadata DB credentials |
| `ANALYTICS_READ_JDBC_URL` | PostgreSQL URL for approved source views and grants |
| `ANALYTICS_READ_USER`, `ANALYTICS_READ_PASSWORD` | Read-only extraction credentials |
| `ANALYTICS_DATASETS_CONFIG` | Path to the protected, operator-managed source catalogue |
| `ANALYTICS_ARTIFACT_URI` | Protected URI root; supported schemes `file:`, `hdfs:`, `s3a:`, `abfs:`; cloud connectors are bundled |
| `SPARK_MASTER` | Spark master URL, default `local[*]`; set cluster master in production |
| `ANALYTICS_WORKER_ID` | Optional unique worker identifier |
| `ANALYTICS_IDLE_POLL_MS` | Queue idle sleep (250–60000), default 1000 |
| `KAFKA_BOOTSTRAP_SERVERS` | Optional Kafka brokers for queue wake-up events; blank disables Kafka and uses PostgreSQL polling only |
| `KAFKA_ANALYTICS_JOB_TOPIC` | Optional wake-up topic; default `erp.analytics.job.queued.v1` |
| `ANALYTICS_JOB_LEASE_SECONDS` | Lease duration (60–7200), default 900 |
| `ANALYTICS_DRIFT_CRON` | Optional; app scheduler property `analytics.drift.cron`, default daily 02:30 UTC |

Run the worker:

```sh
./gradlew :spark-analytics:runWorker --no-daemon
```

Run it as a dedicated service identity with bounded CPU/memory, concurrency controls, timeouts, log rotation and alerting. Put the worker behind process/container supervision. For a Spark cluster, deploy on appropriately sized nodes and configure executor memory/cores and artifact storage permissions; local mode is for development only.

### Source aliases required by built-in jobs

`sales_orders`: `customer_id`, `order_id`, `order_date`, `net_amount`. For `CUSTOMER_CHURN_RISK`, also expose `churn_label` (binary 0/1) and any numeric feature aliases selected in the job request; the example UI uses `net_amount` only as a starter feature and is not a recommended production feature set.

`order_lines`: `transaction_id`, `product_id` (transaction IDs should be unique within a tenant scope; the reader's allowed contact scopes filter input rows).

`inventory_daily`: `product_id`, `warehouse_id`, `demand_date`, `demand_qty`; stock-out labels/features must be added to the operator-defined view if running `STOCKOUT_RISK`.

`receivables`: `customer_id`, `invoice_id`, `due_date`, `paid_date`, `invoice_amount`, `overdue_label`; the time-split risk model uses `due_date` as decision-date cutoff. Feature columns must be values known at that decision time, not post-outcome fields. Prefer leakage-reviewed features such as invoice amount, terms and strictly historical payment behavior.

The included example mapping is deliberately a **template**, not a verified mapping of the current ERP entities. The inventory template includes optional `recent_demand`, `on_hand`, `lead_time_days`, and `stockout_label` fields for supervised stock-out training. The receivables template includes leakage-reviewed aliases for historical overdue rate, payment delay and payment terms. Build approved SQL views in staging and confirm all aliases, historical labels, joins, currencies, deleted records and document lifecycle statuses before use.

## API overview

All endpoints are under `/api/analytics` and follow the app's configured basic authentication and ERP policy authorization.

- `POST /jobs` — validates a logical job type and dataset key, checks the caller's tenant/scope grants, then durably queues a job. Use an idempotency key for clients that may retry HTTP requests.
- `GET /jobs?tenantId=...` — list the caller's own jobs in that tenant; optional `status`.
- `GET /jobs/{id}?tenantId=...` — show own job.
- `POST /jobs/{id}/cancel?tenantId=...` — cancel a queued job or request cancellation of a running job.
- `GET /models?tenantId=...` — model-version registry. Analysts see their own model metadata; separately authorized model operators can inspect registry-wide models in the tenant.
- `GET /experiments?tenantId=...` — recorded experiment runs for the caller.
- `GET /drift?tenantId=...` — drift metrics.
- Submit `MODEL_SCORING` on the same dataset as the deployed pipeline with `modelName` and `modelVersion` parameters to write scored rows to protected artifact storage.
- `POST /models/{name}/{version}/review?tenantId=...` — approve/reject candidate.
- `POST /models/{name}/{version}/promote?tenantId=...` — promote a validated model.
- `POST /models/{name}/{version}/rollback?tenantId=...` — reactivate a retired version.

Example executive sales trend analysis (monthly calendar periods):

```http
POST /api/analytics/jobs
Content-Type: application/json
Authorization: Basic <your ERP credentials>
```

```json
{
  "tenantId": "tenant-a",
  "datasetKey": "sales_orders",
  "jobType": "SALES_TREND_ANALYSIS",
  "idempotencyKey": "sales-trends-tenant-a-2026-10",
  "parameters": {}
}
```

The ERP writes a versioned erp.analytics.job.queued.v1 notification to Kafka through a PostgreSQL transactional outbox. The Spark worker consumes notifications as low-latency wake-ups, then claims authoritative job rows with PostgreSQL leases. If Kafka is unavailable, the worker continues polling PostgreSQL; duplicate Kafka delivery is safe because the database queue owns job state. The outbox is at-least-once, so consumers must be idempotent. Job status/results remain visible through the ERP API and shared analytics metadata database. The result is written as tenant-scoped Parquet under the protected artifact root. Missing calendar months between the first and last observed order month are emitted with zero revenue/orders; growth percentages are null when the comparison baseline is zero. Revenue is the sum of the configured `net_amount` alias, so normalize currencies and exclude cancelled/draft documents in the approved source view before running this report.

Example RFM job:

```http
POST /api/analytics/jobs
Content-Type: application/json
Authorization: Basic <your ERP credentials>
```

```json
{
  "tenantId": "tenant-a",
  "datasetKey": "sales_orders",
  "jobType": "RFM",
  "idempotencyKey": "rfm-tenant-a-2026-10-10",
  "parameters": { "asOfDate": "2026-10-10" }
}
```

Example demand model backtest:

```json
{
  "tenantId": "tenant-a",
  "datasetKey": "inventory_daily",
  "jobType": "DEMAND_FORECAST",
  "idempotencyKey": "demand-backtest-2026-10",
  "parameters": { "trainThroughDate": "2026-09-30" }
}
```

Example receivables-risk review queue:

```json
{
  "tenantId": "tenant-a",
  "datasetKey": "receivables",
  "jobType": "RECEIVABLES_RISK",
  "idempotencyKey": "receivable-risk-tenant-a-2026-10",
  "parameters": {
    "trainThroughDate": "2026-06-30",
    "reviewThreshold": 0.65,
    "featureColumns": ["invoice_amount", "customer_overdue_rate", "historical_average_payment_delay", "payment_terms_days"]
  }
}
```

Example product affinity job uses `"datasetKey": "order_lines"`, `"jobType": "MARKET_BASKET"`, and parameters `{"minSupport":0.01,"minConfidence":0.25}`. For ALS recommendations, submit `PRODUCT_RECOMMENDATIONS` with `{"topN":10,"rank":16,"maxIterations":15,"regularization":0.1}`. The output ranks product suggestions for a transaction/basket context; it is not customer-level personalization because the allowlisted source contains only transaction and product identifiers. The implementation requires at least 20 distinct basket-product interactions, 2 baskets, and 2 products.

These payloads require actual grants, configured policy rules, and source aliases; they do not bypass authorization.

## Operations and controls before rollout

- **Idempotency/retries:** the DB unique key and queue leases prevent duplicate claims; transient failures retry with bounded exponential delay. Verify database time and lease behavior under process kill/restart.
- **Artifact isolation:** outputs are tenant/job scoped; deploy with private storage, encryption at rest, retention, backups, and per-tenant access policies. The metadata API should not hand out arbitrary file URIs to browsers.
- **Data access:** filtering is evaluated in PostgreSQL at worker execution time. Each job materializes its scoped JDBC result once in worker-local disk cache so repeated Spark actions use the same extract; the cache is released after the job. The source rows are not retained as a durable snapshot. Lineage retains the source mapping, extraction time and scope fingerprint; for stronger long-term reproducibility, feed immutable database snapshots or versioned views into the platform.
- **Mapping integrity:** model manifests bind the source table, selected source-column aliases, tenant column, scope column/type and active scope grant fingerprint. Scoring/drift jobs fail closed if any of them differ from the training mapping. Model and drift metadata is scoped to its creator unless a separately authorized model operator accesses it.
- **Auditability:** experiment inputs, model manifests, metrics, reviewers and model lifecycle events are persisted. Protect database backups and restrict direct metadata-table writes.
- **Monitoring:** scrape application/worker metrics and logs externally; alert on queue age, retries, dead jobs, lease loss, Spark failures, model quality and PSI alerts. PSI measurements are available via the drift API.
- **Data lineage:** manifests record tenant/model/job/dataset, parameters, Spark version, input-source reference, scope fingerprint, output URI, and metrics. Pin source snapshot/view version and artifact checksums in a mature production deployment.
- **Capacity:** set max parallel jobs per tenant and total worker concurrency; monitor PostgreSQL load. Use table/view indexes on tenant and scope keys. Partition very large extracts or use immutable snapshots/CDC if repeated scans hurt the OLTP database.
- **Backups/DR:** back up metadata tables and artifact stores together, test restore and model rollback. Avoid overwrite of previously promoted production model artifact paths.
- **Privacy/fairness:** minimise or pseudonymise customer features, define purpose and retention, test error rates across permitted segments, and require human review for financial/credit-related decisions.
- **Tests/release:** exercise migration on a staging PostgreSQL, run end-to-end tenant-isolation tests with separate users and grants, test row revocation during queued/running work, load-test large Spark jobs, and restore a prior production model before merge/release.

## Known boundaries

- Source views/config and scope grants must be created for the real ERP schema; this repository intentionally cannot infer safe tenant/contact join paths automatically.
- Demand forecasting currently evaluates an out-of-time holdout set; it is not yet a full future-horizon inventory replenishment planner. For meaningful demand, provide one row per product/warehouse/business day (including zero-demand days), account for stockout-censored sales, holidays and promotions, and implement rolling-origin evaluation.
- The built-in historical CLV measure is a descriptive proxy; predictive CLV needs retention/churn, margin, discounting and calibration assumptions.
- Drift monitoring focuses on numeric-feature PSI, not full target drift, concept drift, fairness or automatic retraining.
- Full production sign-off still requires deployment-specific integration tests, secret management, resource quotas, on-call alerts and security review.

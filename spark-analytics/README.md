# Spark Analytics and Spark ML

This is a separately built analytics worker module, not a dependency of the Spring Boot web process. It keeps Spark's large runtime and transitive dependencies out of normal ERP CRUD requests.

## Included baseline

- Apache Spark SQL and Spark MLlib (Spark 4.0.1, Scala 2.13).
- Reusable Spark session provider with UTC timestamps and adaptive query execution.
- A reproducible K-means customer-segmentation pipeline with input validation, null handling, fixed seed, saved model, Parquet predictions, and per-segment counts.
- A result record suitable for later integration with a secured job API.

## Build

Requires JDK 17 and Gradle:

```sh
./gradlew :spark-analytics:test
./gradlew :spark-analytics:build
```

For local experimentation, set `SPARK_MASTER=local[*]`. In production, provide the cluster master via deployment configuration and run jobs as isolated workers. Do not expose Spark's UI or master ports publicly.

## Production integration checklist

Before connecting this to ERP data, add a least-privilege, read-only extraction layer that applies tenant/contact authorization before data leaves PostgreSQL. Use stable, versioned snapshots rather than reading mutable transactional tables throughout a long-running job. Keep PII out of features unless there is a documented purpose and retention policy.

Deploy jobs through a queue/scheduler with idempotency keys, concurrency limits, timeouts, cancellation, retry policy, audit events, and job status persistence. Store models and output datasets in versioned object storage or protected shared storage; publish a model only after validation. Track input snapshot, feature schema, Spark version, algorithm parameters, model metrics, and artifact checksums. Add drift monitoring and model rollback before using predictions to drive business actions.

## Next candidates

- RFM and cohort analysis for sales/CRM.
- Product basket association rules (FP-Growth).
- Demand forecasting and inventory anomaly detection.
- Credit/payment-risk scoring only with approved features, evaluation, explainability, and human review.
- MLflow-compatible experiment/model registry integration.

The example is a foundation, not a claim that the full operational controls above are already implemented. The job currently overwrites its configured output paths; production deployments should pass unique versioned paths and promote artifacts after validation.

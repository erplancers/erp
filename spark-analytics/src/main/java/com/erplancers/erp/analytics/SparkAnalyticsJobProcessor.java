package com.erplancers.erp.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/** Dispatches allowlisted BI/ML jobs over server-configured, row-scoped source datasets. */
public final class SparkAnalyticsJobProcessor implements AnalyticsJobHandler {
    private final AnalyticsDatasetCatalog catalog;
    private final ScopedPostgresReader reader;
    private final AnalyticsJobStore store;
    private final String artifactRoot;
    private final ObjectMapper json = new ObjectMapper();

    public SparkAnalyticsJobProcessor(AnalyticsDatasetCatalog catalog, ScopedPostgresReader reader,
                                      AnalyticsJobStore store, String artifactRoot) {
        this.catalog = Objects.requireNonNull(catalog);
        this.reader = Objects.requireNonNull(reader);
        this.store = Objects.requireNonNull(store);
        if (artifactRoot == null || artifactRoot.isBlank()) throw new IllegalArgumentException("ANALYTICS_ARTIFACT_URI is required");
        this.artifactRoot = artifactRoot.replaceAll("/+$", "");
        if (!(this.artifactRoot.startsWith("file:") || this.artifactRoot.startsWith("hdfs:")
                || this.artifactRoot.startsWith("s3a:") || this.artifactRoot.startsWith("abfs:"))) {
            throw new IllegalArgumentException("Artifact URI must use file:, hdfs:, s3a:, or abfs:");
        }
    }

    @Override
    public JobResult execute(AnalyticsJobStore.Job job, JobExecutionContext context) throws Exception {
        Map<String, Object> p = json.readValue(job.parametersJson(), new TypeReference<>() {});
        AnalyticsDatasetCatalog.DatasetDefinition definition = catalog.require(job.datasetKey());
        context.checkCancellation();
        String scopeFingerprint = reader.scopeFingerprint(definition, job);
        Dataset<Row> rawSource = reader.read(definition, job);
        rawSource.sparkSession().sparkContext().setJobGroup(job.id(), "ERP analytics " + job.jobType(), true);
        Dataset<Row> source = rawSource.persist(org.apache.spark.storage.StorageLevel.DISK_ONLY());
        try {
        // Materialize the scoped JDBC result once so subsequent Spark actions share one extract.
        long sourceRows = source.count();
        String output = artifactRoot + "/" + safeSegment(job.tenantId()) + "/" + job.id()
                + "/attempt-" + job.attemptCount();
        String inputEvidence = "source=" + definition.table() + "; rows=" + sourceRows
                + "; extractedAt=" + Instant.now() + "; scopeFingerprint=" + scopeFingerprint;
        Map<String, Object> metrics = new LinkedHashMap<>();
        String algorithm = job.jobType();
        String modelPath = null;
        String candidateArtifactUri = null;
        String baselinePath = null;
        List<String> modelFeatureColumns = List.of();
        List<DriftObservation> driftObservations = List.of();
        String outputPath = output + "/unassigned";

        switch (job.jobType()) {
            case "RFM" -> {
                Dataset<Row> result = SalesIntelligence.rfm(source, "customer_id", "order_id",
                        "order_date", "net_amount", dateParameter(p, "asOfDate"));
                outputPath = write(result, output + "/rfm");
                metrics.put("customers", result.count());
                metrics.put("outputPath", outputPath);
                algorithm = "RFM";
            }
            case "COHORT_ANALYSIS" -> {
                Dataset<Row> result = SalesIntelligence.cohorts(source, "customer_id", "order_date");
                outputPath = write(result, output + "/cohorts");
                metrics.put("cohortPeriods", result.count());
                metrics.put("outputPath", outputPath);
                algorithm = "customer-cohort-retention";
            }
            case "CUSTOMER_LIFETIME_VALUE" -> {
                Dataset<Row> result = SalesIntelligence.customerLifetimeValue(source, "customer_id",
                        "order_id", "order_date", "net_amount");
                outputPath = write(result, output + "/customer-lifetime-value");
                metrics.put("customers", result.count());
                metrics.put("outputPath", outputPath);
                metrics.put("measure", "historical revenue and annualized estimate; not discounted predictive CLV");
                algorithm = "historical-annualized-clv";
            }
            case "CUSTOMER_SEGMENTATION" -> {
                Dataset<Row> rfm = SalesIntelligence.rfm(source, "customer_id", "order_id",
                        "order_date", "net_amount", dateParameter(p, "asOfDate"));
                int clusters = intParameter(p, "clusters", 5, 2, 100);
                int maxIterations = intParameter(p, "maxIterations", 100, 1, 1000);
                List<String> features = stringList(p, "featureColumns",
                        List.of("recency_days", "frequency", "monetary_value"));
                requireAliases(rfm, features);
                modelPath = output + "/model";
                String predictionsPath = output + "/predictions";
                MiningResult result = CustomerSegmentationJob.run(rfm, "customer_id", features, clusters,
                        maxIterations, modelPath, predictionsPath);
                metrics.put("rows", result.inputRows());
                metrics.put("segments", result.summaries());
                metrics.putAll(result.metrics());
                baselinePath = output + "/monitoring-baseline";
                write(SalesIntelligence.selectColumns(rfm, features.toArray(String[]::new)), baselinePath);
                modelFeatureColumns = features;
                metrics.put("modelPath", modelPath);
                outputPath = predictionsPath;
                algorithm = "k-means-customer-segmentation";
            }
            case "DEMAND_FORECAST" -> {
                String splitDate = requiredString(p, "trainThroughDate");
                modelPath = output + "/model";
                InventoryMining.ForecastResult result = InventoryMining.forecastDemand(source,
                        "product_id", "warehouse_id", "demand_date", "demand_qty", splitDate, modelPath);
                outputPath = write(result.predictions(), output + "/forecast-holdout");
                metrics.put("rmse", result.rmse());
                metrics.put("mae", result.mae());
                metrics.put("r2", result.r2());
                baselinePath = output + "/monitoring-baseline";
                write(result.baselineFeatures(), baselinePath);
                modelFeatureColumns = List.of("_lag_1", "_lag_7", "_lag_14", "_day_of_week");
                metrics.put("trainingRows", result.trainingRows());
                metrics.put("holdoutRows", result.holdoutRows());
                metrics.put("predictionsPath", outputPath);
                metrics.put("modelPath", modelPath);
                algorithm = "time-ordered-lag-linear-regression";
            }
            case "STOCKOUT_RISK" -> {
                List<String> features = stringList(p, "featureColumns", List.of("recent_demand", "on_hand", "lead_time_days"));
                String label = stringParameter(p, "labelColumn", "stockout_label");
                requireAliases(source, features);
                requireAliases(source, List.of(label));
                modelPath = output + "/model";
                InventoryMining.StockoutResult result = InventoryMining.trainStockoutRiskTimeOrdered(source,
                        features, label, "demand_date", List.of("product_id", "warehouse_id"),
                        requiredString(p, "trainThroughDate"), modelPath);
                outputPath = write(result.predictions(), output + "/stockout-risk");
                metrics.put("areaUnderRoc", result.areaUnderRoc());
                metrics.put("weightedPrecision", result.weightedPrecision());
                metrics.put("weightedRecall", result.weightedRecall());
                baselinePath = output + "/monitoring-baseline";
                write(result.baselineFeatures(), baselinePath);
                modelFeatureColumns = features;
                metrics.put("trainingRows", result.trainingRows());
                metrics.put("holdoutRows", result.holdoutRows());
                metrics.put("predictionsPath", outputPath);
                metrics.put("modelPath", modelPath);
                algorithm = "stockout-logistic-regression";
            }
            case "INVENTORY_ANOMALY" -> {
                double threshold = doubleParameter(p, "zThreshold", 3.0, 2.0, 8.0);
                Dataset<Row> result = InventoryMining.demandAnomalies(source,
                        "product_id", "warehouse_id", "demand_date", "demand_qty", threshold);
                outputPath = write(result, output + "/demand-anomalies");
                metrics.put("zThreshold", threshold);
                metrics.put("anomalyRows", result.count());
                metrics.put("outputPath", outputPath);
                algorithm = "rolling-28-observation-demand-zscore";
            }
            case "MARKET_BASKET" -> {
                double support = doubleParameter(p, "minSupport", 0.01, 0.0001, 1.0);
                double confidence = doubleParameter(p, "minConfidence", 0.25, 0.0001, 1.0);
                modelPath = output + "/model";
                MarketBasketMining.Result result = MarketBasketMining.mine(source,
                        "transaction_id", "product_id", support, confidence, modelPath);
                String frequentPath = write(result.frequentItemsets(), output + "/frequent-itemsets");
                String rulesPath = write(result.associationRules(), output + "/association-rules");
                metrics.put("transactions", result.transactionCount());
                metrics.put("frequentItemsetsPath", frequentPath);
                metrics.put("associationRulesPath", rulesPath);
                metrics.put("modelPath", modelPath);
                algorithm = "fp-growth-market-basket";
            }
            case "RECEIVABLES_RISK" -> {
                List<String> features = stringList(p, "featureColumns",
                        List.of("invoice_amount", "customer_overdue_rate", "historical_average_payment_delay", "payment_terms_days"));
                String label = stringParameter(p, "labelColumn", "overdue_label");
                requireAliases(source, features);
                requireAliases(source, List.of(label));
                double threshold = doubleParameter(p, "reviewThreshold", 0.65, 0.0, 1.0);
                modelPath = output + "/model";
                ReceivablesRiskModel.Result result = ReceivablesRiskModel.trainTimeOrdered(source, features, label, "due_date", List.of("invoice_id", "customer_id"),
                        requiredString(p, "trainThroughDate"), threshold, modelPath);
                outputPath = write(result.reviewQueue(), output + "/receivables-review-queue");
                metrics.put("areaUnderRoc", result.areaUnderRoc());
                metrics.put("trainingRows", result.trainingRows());
                metrics.put("validationRows", result.validationRows());
                metrics.put("weightedPrecision", result.weightedPrecision());
                metrics.put("weightedRecall", result.weightedRecall());
                metrics.put("reviewThreshold", result.reviewThreshold());
                metrics.put("decisionPolicy", "HUMAN_REVIEW_ONLY");
                metrics.put("reviewQueuePath", outputPath);
                metrics.put("weightedPrecision", result.weightedPrecision());
                metrics.put("weightedRecall", result.weightedRecall());
                baselinePath = output + "/monitoring-baseline";
                write(result.baselineFeatures(), baselinePath);
                modelFeatureColumns = features;
                metrics.put("modelPath", modelPath);
                algorithm = "overdue-payment-logistic-regression-human-review";
            }
            case "CUSTOMER_CHURN_RISK" -> {
                List<String> features = stringList(p, "featureColumns", List.of("net_amount"));
                String label = stringParameter(p, "labelColumn", "churn_label");
                requireAliases(source, features);
                requireAliases(source, List.of(label, "customer_id", "order_id", "order_date"));
                String selectedAlgorithm = stringParameter(p, "algorithm", "LOGISTIC_REGRESSION");
                double threshold = doubleParameter(p, "reviewThreshold", 0.65, 0.0, 1.0);
                modelPath = output + "/model";
                CustomerChurnMining.Result result = CustomerChurnMining.trainTimeOrdered(source, features, label,
                        "order_date", List.of("customer_id", "order_id"), requiredString(p, "trainThroughDate"),
                        selectedAlgorithm, threshold, modelPath);
                outputPath = write(result.predictions(), output + "/churn-risk-review-queue");
                metrics.put("areaUnderRoc", result.areaUnderRoc());
                metrics.put("weightedPrecision", result.weightedPrecision());
                metrics.put("weightedRecall", result.weightedRecall());
                metrics.put("trainingRows", result.trainingRows());
                metrics.put("holdoutRows", result.holdoutRows());
                metrics.put("algorithm", result.algorithm());
                metrics.put("reviewThreshold", threshold);
                metrics.put("decisionPolicy", "RETENTION_REVIEW_ONLY");
                metrics.put("outputPath", outputPath);
                baselinePath = output + "/monitoring-baseline";
                write(result.baselineFeatures(), baselinePath);
                modelFeatureColumns = features;
                metrics.put("modelPath", modelPath);
                algorithm = "customer-churn-risk-" + result.algorithm().toLowerCase(Locale.ROOT);
            }
            case "MODEL_SCORING" -> {
                String modelName = requiredString(p, "modelName");
                int version = intParameter(p, "modelVersion", 1, 1, Integer.MAX_VALUE);
                AnalyticsJobStore.ModelVersion registered = store.findModelVersion(job.tenantId(), modelName, version);
                if (registered == null || !"PRODUCTION".equals(registered.status())) {
                    throw new IllegalArgumentException("Scoring requires a PRODUCTION model version in this tenant");
                }
                JsonNode manifestNode = json.readTree(registered.manifestJson());
                if (!job.datasetKey().equals(manifestNode.path("datasetKey").asText())) {
                    throw new SecurityException("Dataset key does not match model training dataset");
                }
                String configuredProjections = definition.projections().stream()
                        .map(projection -> projection.sourceColumn() + ":" + projection.logicalAlias())
                        .collect(Collectors.joining(","));
                if (!definition.table().equals(manifestNode.path("sourceTable").asText(""))
                        || !configuredProjections.equals(manifestNode.path("sourceProjections").asText(""))) {
                    throw new SecurityException("Approved source mapping changed since model training");
                }
                if (!definition.scopeType().equals(manifestNode.path("scopeType").asText(""))
                        || !definition.tenantColumn().equals(manifestNode.path("tenantColumn").asText(""))
                        || !definition.scopeColumn().equals(manifestNode.path("scopeColumn").asText(""))) {
                    throw new SecurityException("Tenant/scope filter mapping changed since model training");
                }
                if (!scopeFingerprint.equals(manifestNode.path("scopeFingerprint").asText())) {
                    throw new SecurityException("Current tenant/entity grants differ from the model's training scope");
                }
                String tenantArtifactPrefix = artifactRoot + "/" + safeSegment(job.tenantId()) + "/";
                if (registered.artifactUri() == null || !registered.artifactUri().startsWith(tenantArtifactPrefix)) {
                    throw new SecurityException("Model artifact is outside this tenant's protected artifact namespace");
                }
                modelPath = registered.artifactUri();
                org.apache.spark.ml.PipelineModel scoringModel = org.apache.spark.ml.PipelineModel.load(modelPath);
                List<String> features = readStringArray(manifestNode.path("featureColumns").toString());
                double reviewThreshold = doubleParameter(p, "reviewThreshold", 0.65, 0.0, 1.0);
                Dataset<Row> scoringInput;
                switch (modelName) {
                    case "customer_segmentation" -> {
                        Dataset<Row> rfm = SalesIntelligence.rfm(source, "customer_id", "order_id",
                                "order_date", "net_amount", dateParameter(p, "asOfDate"));
                        requireAliases(rfm, features);
                        scoringInput = scoringModel.transform(rfm).select("customer_id", "segment");
                        algorithm = "customer-segmentation-batch-scoring";
                    }
                    case "demand_forecast" -> {
                        Dataset<Row> featureFrame = InventoryMining.demandFeatureFrame(source,
                                "product_id", "warehouse_id", "demand_date", "demand_qty");
                        requireAliases(featureFrame, features);
                        scoringInput = scoringModel.transform(featureFrame)
                                .withColumn("forecast_demand", org.apache.spark.sql.functions.greatest(
                                        org.apache.spark.sql.functions.col("prediction"), org.apache.spark.sql.functions.lit(0.0)))
                                .select("product_id", "warehouse_id", "demand_date", "forecast_demand");
                        algorithm = "demand-forecast-batch-scoring";
                    }
                    case "stockout_risk" -> {
                        requireAliases(source, features);
                        java.util.LinkedHashSet<String> selected = new java.util.LinkedHashSet<>(
                                List.of("product_id", "warehouse_id", "demand_date"));
                        selected.addAll(features);
                        scoringInput = source.select(selected.iterator().next(),
                                selected.stream().skip(1).toArray(String[]::new));
                        Dataset<Row> raw = scoringModel.transform(scoringInput);
                        scoringInput = raw.withColumn("stockout_risk_score",
                                        org.apache.spark.ml.functions.vector_to_array(
                                                org.apache.spark.sql.functions.col("probability"), "float64").getItem(1))
                                .withColumn("human_review_required", org.apache.spark.sql.functions.col("stockout_risk_score").geq(0.65))
                                .withColumn("recommended_action", org.apache.spark.sql.functions.lit("REVIEW_INVENTORY"))
                                .drop("features", "rawPrediction", "probability", "prediction");
                        algorithm = "stockout-risk-batch-scoring";
                    }
                    case "receivables_risk" -> {
                        requireAliases(source, features);
                        java.util.LinkedHashSet<String> selected = new java.util.LinkedHashSet<>(
                                List.of("invoice_id", "customer_id", "due_date"));
                        selected.addAll(features);
                        Dataset<Row> rawInput = source.select(selected.iterator().next(),
                                selected.stream().skip(1).toArray(String[]::new));
                        Dataset<Row> raw = scoringModel.transform(rawInput);
                        scoringInput = raw.withColumn("overdue_risk_score",
                                        org.apache.spark.ml.functions.vector_to_array(
                                                org.apache.spark.sql.functions.col("probability"), "float64").getItem(1))
                                .withColumn("human_review_required", org.apache.spark.sql.functions.col("overdue_risk_score").geq(reviewThreshold))
                                .withColumn("recommended_action", org.apache.spark.sql.functions.lit("HUMAN_REVIEW_ONLY"))
                                .drop("features", "rawPrediction", "probability", "prediction");
                        algorithm = "receivables-risk-batch-scoring-human-review";
                    }
                    case "customer_churn_risk" -> {
                        requireAliases(source, features);
                        java.util.LinkedHashSet<String> selected = new java.util.LinkedHashSet<>(
                                List.of("customer_id", "order_id", "order_date"));
                        selected.addAll(features);
                        Dataset<Row> rawInput = source.select(selected.iterator().next(),
                                selected.stream().skip(1).toArray(String[]::new));
                        Dataset<Row> raw = scoringModel.transform(rawInput);
                        scoringInput = raw.withColumn("churn_risk_score",
                                        org.apache.spark.ml.functions.vector_to_array(
                                                org.apache.spark.sql.functions.col("probability"), "float64").getItem(1))
                                .withColumn("human_review_required", org.apache.spark.sql.functions.col("churn_risk_score").geq(reviewThreshold))
                                .withColumn("recommended_action", org.apache.spark.sql.functions.lit("RETENTION_REVIEW_ONLY"))
                                .drop("features", "rawPrediction", "probability", "prediction");
                        algorithm = "customer-churn-risk-batch-scoring-human-review";
                    }
                    default -> throw new IllegalArgumentException("Model is not eligible for pipeline scoring: " + modelName);
                }
                outputPath = write(scoringInput, output + "/model-scoring");
                metrics.put("modelName", modelName);
                metrics.put("modelVersion", version);
                metrics.put("rowsScored", scoringInput.count());
                metrics.put("outputPath", outputPath);
                metrics.put("decisionPolicy", modelName.equals("receivables_risk") ? "HUMAN_REVIEW_ONLY" : "REVIEW_REQUIRED_FOR_ACTION");
            }
            case "DATA_DRIFT" -> {
                String modelName = requiredString(p, "modelName");
                int version = intParameter(p, "modelVersion", 1, 1, Integer.MAX_VALUE);
                AnalyticsJobStore.ModelVersion model = store.findModelVersion(job.tenantId(), modelName, version);
                if (model == null) throw new IllegalArgumentException("Model version not found in this tenant");
                if (!"PRODUCTION".equals(model.status())) throw new IllegalArgumentException("Drift checks require the current PRODUCTION model version");
                var manifestNode = json.readTree(model.manifestJson());
                if (!job.datasetKey().equals(manifestNode.path("datasetKey").asText())) {
                    throw new SecurityException("Dataset key does not match model training dataset");
                }
                String expectedTable = manifestNode.path("sourceTable").asText("");
                String expectedProjections = manifestNode.path("sourceProjections").asText("");
                String configuredProjections = definition.projections().stream()
                        .map(projection -> projection.sourceColumn() + ":" + projection.logicalAlias())
                        .collect(Collectors.joining(","));
                if (!definition.table().equals(expectedTable) || !configuredProjections.equals(expectedProjections)) {
                    throw new SecurityException("Approved source mapping changed since the model baseline was created");
                }
                if (!definition.scopeType().equals(manifestNode.path("scopeType").asText(""))
                        || !definition.tenantColumn().equals(manifestNode.path("tenantColumn").asText(""))
                        || !definition.scopeColumn().equals(manifestNode.path("scopeColumn").asText(""))) {
                    throw new SecurityException("Tenant/scope filter mapping changed since model training");
                }
                if (!scopeFingerprint.equals(manifestNode.path("scopeFingerprint").asText())) {
                    throw new SecurityException("Current tenant/entity grants differ from the model baseline; retrain before drift comparison");
                }
                String baselineUri = manifestNode.path("baselineUri").asText("");
                if (baselineUri.isBlank()) throw new IllegalArgumentException("This model version has no numeric drift baseline");
                String allowedArtifactPrefix = artifactRoot + "/" + safeSegment(job.tenantId()) + "/";
                if (!baselineUri.startsWith(allowedArtifactPrefix)) {
                    throw new SecurityException("Model baseline artifact is outside this tenant's protected artifact namespace");
                }
                List<String> features = stringList(p, "featureColumns",
                        readStringArray(manifestNode.path("featureColumns").toString()));
                List<String> trainedFeatures = readStringArray(manifestNode.path("featureColumns").toString());
                if (trainedFeatures.isEmpty() || !trainedFeatures.containsAll(features)) {
                    throw new IllegalArgumentException("Drift features must be a subset of model baseline features");
                }
                Dataset<Row> baseline = source.sparkSession().read().parquet(baselineUri);
                Dataset<Row> current;
                switch (modelName) {
                    case "customer_segmentation" -> current = SalesIntelligence.rfm(source, "customer_id", "order_id",
                            "order_date", "net_amount", dateParameter(p, "asOfDate"));
                    case "demand_forecast" -> current = InventoryMining.demandFeatureFrame(source,
                            "product_id", "warehouse_id", "demand_date", "demand_qty");
                    case "stockout_risk", "receivables_risk" -> current = source;
                    case "customer_churn_risk" -> current = source;
                    default -> throw new IllegalArgumentException("Drift monitoring is not configured for model: " + modelName);
                }
                double alertThreshold = doubleParameter(p, "alertThreshold", 0.25, 0.01, 10.0);
                List<ModelMonitoring.DriftMetric> drift = ModelMonitoring.populationStabilityIndex(
                        baseline, current, features, alertThreshold);
                driftObservations = drift.stream().map(metric -> new DriftObservation(modelName, version,
                        metric.feature(), metric.populationStabilityIndex(), metric.status(),
                        metric.baselineNonNullRows(), metric.currentNonNullRows(), baselineUri, inputEvidence)).toList();
                metrics.put("modelName", modelName);
                metrics.put("modelVersion", version);
                metrics.put("driftMetrics", drift.stream().map(metric -> Map.of(
                        "feature", metric.feature(), "psi", metric.populationStabilityIndex(),
                        "status", metric.status(), "baselineRows", metric.baselineNonNullRows(),
                        "currentRows", metric.currentNonNullRows())).toList());
                outputPath = "postgresql:analytics_drift_metric";
                algorithm = "population-stability-index";
            }
            default -> throw new IllegalArgumentException("Unsupported analytics job type: " + job.jobType());
        }

        context.checkCancellation();
        if (Set.of("CUSTOMER_SEGMENTATION", "CUSTOMER_CHURN_RISK", "DEMAND_FORECAST", "STOCKOUT_RISK", "RECEIVABLES_RISK").contains(job.jobType())) {
            candidateArtifactUri = modelPath;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> safeMetrics = (Map<String, Object>) jsonSafe(metrics);
        String manifest = json.writeValueAsString(Map.ofEntries(
                Map.entry("modelType", algorithm), Map.entry("jobId", job.id()),
                Map.entry("tenantId", job.tenantId()), Map.entry("createdBy", job.requestedBy()),
                Map.entry("datasetKey", job.datasetKey()), Map.entry("sourceTable", definition.table()),
                Map.entry("sourceProjections", definition.projections().stream()
                        .map(projection -> projection.sourceColumn() + ":" + projection.logicalAlias())
                        .collect(Collectors.joining(","))),
                Map.entry("scopeType", definition.scopeType()),
                Map.entry("tenantColumn", definition.tenantColumn()),
                Map.entry("scopeColumn", definition.scopeColumn()),
                Map.entry("sparkVersion", source.sparkSession().version()),
                Map.entry("createdAt", Instant.now().toString()), Map.entry("parameters", p),
                Map.entry("metrics", safeMetrics), Map.entry("inputEvidence", inputEvidence),
                Map.entry("outputPath", outputPath), Map.entry("baselineUri", baselinePath == null ? "" : baselinePath),
                Map.entry("featureColumns", modelFeatureColumns), Map.entry("scopeFingerprint", scopeFingerprint)));
        String resultJson = json.writeValueAsString(Map.of(
                "jobId", job.id(), "algorithm", algorithm, "metrics", safeMetrics,
                "outputPath", outputPath, "modelPath", modelPath == null ? "" : modelPath,
                "humanReviewRequired", job.jobType().equals("RECEIVABLES_RISK")));
        return new JobResult(resultJson, algorithm, job.parametersJson(), json.writeValueAsString(safeMetrics),
                inputEvidence, candidateArtifactUri, manifest, driftObservations);
        } finally {
            source.unpersist(false);
            source.sparkSession().sparkContext().clearJobGroup();
        }
    }

    private static Object jsonSafe(Object value) {
        if (value instanceof Double d && !Double.isFinite(d)) return null;
        if (value instanceof Float f && !Float.isFinite(f)) return null;
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> safe = new LinkedHashMap<>();
            map.forEach((key, item) -> safe.put(String.valueOf(key), jsonSafe(item)));
            return safe;
        }
        if (value instanceof Iterable<?> values) {
            List<Object> safe = new ArrayList<>();
            for (Object item : values) safe.add(jsonSafe(item));
            return safe;
        }
        return value;
    }

    private static String write(Dataset<Row> dataset, String path) {
        dataset.write().mode("overwrite").parquet(path);
        return path;
    }

    private static void requireAliases(Dataset<Row> data, List<String> names) {
        Set<String> available = Set.of(data.columns());
        for (String name : names) if (!available.contains(name)) {
            throw new IllegalArgumentException("Configured dataset does not expose logical column: " + name);
        }
    }

    private static LocalDate dateParameter(Map<String, Object> p, String name) {
        String value = stringParameter(p, name, LocalDate.now(ZoneOffset.UTC).toString());
        return LocalDate.parse(value);
    }

    private static String requiredString(Map<String, Object> p, String name) {
        String value = stringParameter(p, name, "");
        if (value.isBlank()) throw new IllegalArgumentException("Parameter '" + name + "' is required");
        return value;
    }

    private static String stringParameter(Map<String, Object> p, String name, String fallback) {
        Object v = p.get(name);
        if (v == null) return fallback;
        if (!(v instanceof String s) || s.length() > 500) throw new IllegalArgumentException("Invalid string parameter: " + name);
        return s;
    }

    private static int intParameter(Map<String, Object> p, String name, int fallback, int min, int max) {
        Object v = p.get(name);
        if (v == null) return fallback;
        if (!(v instanceof Number n)) throw new IllegalArgumentException("Invalid numeric parameter: " + name);
        int value = n.intValue();
        if (value < min || value > max || value != n.doubleValue()) throw new IllegalArgumentException("Out-of-range parameter: " + name);
        return value;
    }

    private static double doubleParameter(Map<String, Object> p, String name, double fallback, double min, double max) {
        Object v = p.get(name);
        if (v == null) return fallback;
        if (!(v instanceof Number n)) throw new IllegalArgumentException("Invalid numeric parameter: " + name);
        double value = n.doubleValue();
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Out-of-range parameter: " + name);
        return value;
    }

    private List<String> readStringArray(String arrayJson) throws IOException {
        return json.readValue(arrayJson, new TypeReference<List<String>>() {});
    }

    private static List<String> stringList(Map<String, Object> p, String name, List<String> fallback) {
        Object v = p.get(name);
        if (v == null) return fallback;
        if (!(v instanceof List<?> values) || values.isEmpty() || values.size() > 64) {
            throw new IllegalArgumentException("Invalid feature list: " + name);
        }
        return values.stream().map(String.class::cast).distinct().collect(Collectors.toList());
    }

    private static String safeSegment(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}

package com.erplancers.erp.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final String artifactRoot;
    private final ObjectMapper json = new ObjectMapper();

    public SparkAnalyticsJobProcessor(AnalyticsDatasetCatalog catalog, ScopedPostgresReader reader, String artifactRoot) {
        this.catalog = Objects.requireNonNull(catalog);
        this.reader = Objects.requireNonNull(reader);
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
        Dataset<Row> source = reader.read(definition, job);
        String output = artifactRoot + "/" + safeSegment(job.tenantId()) + "/" + job.id();
        String inputEvidence = "source=" + definition.table() + "; filtered-at-execution=" + Instant.now();
        Map<String, Object> metrics = new LinkedHashMap<>();
        String algorithm = job.jobType();
        String modelPath = null;
        String outputPath;

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
                InventoryMining.StockoutResult result = InventoryMining.trainStockoutRisk(source,
                        features, label, doubleParameter(p, "trainFraction", 0.8, 0.5, 0.94), modelPath);
                outputPath = write(result.predictions(), output + "/stockout-risk");
                metrics.put("areaUnderRoc", result.areaUnderRoc());
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
                        List.of("invoice_amount", "days_past_due", "customer_overdue_rate", "invoice_age_days"));
                String label = stringParameter(p, "labelColumn", "overdue_label");
                requireAliases(source, features);
                requireAliases(source, List.of(label));
                double threshold = doubleParameter(p, "reviewThreshold", 0.65, 0.0, 1.0);
                modelPath = output + "/model";
                ReceivablesRiskModel.Result result = ReceivablesRiskModel.train(source, features, label,
                        threshold, doubleParameter(p, "trainFraction", 0.8, 0.5, 0.94), modelPath);
                outputPath = write(result.reviewQueue(), output + "/receivables-review-queue");
                metrics.put("areaUnderRoc", result.areaUnderRoc());
                metrics.put("trainingRows", result.trainingRows());
                metrics.put("validationRows", result.validationRows());
                metrics.put("reviewThreshold", result.reviewThreshold());
                metrics.put("decisionPolicy", "HUMAN_REVIEW_ONLY");
                metrics.put("reviewQueuePath", outputPath);
                metrics.put("modelPath", modelPath);
                algorithm = "overdue-payment-logistic-regression-human-review";
            }
            default -> throw new IllegalArgumentException("Unsupported analytics job type: " + job.jobType());
        }

        context.checkCancellation();
        String manifest = json.writeValueAsString(Map.of(
                "modelType", algorithm, "jobId", job.id(), "tenantId", job.tenantId(),
                "datasetKey", job.datasetKey(), "sparkVersion", source.sparkSession().version(),
                "createdAt", Instant.now().toString(), "parameters", p, "metrics", metrics,
                "inputEvidence", inputEvidence, "outputPath", outputPath));
        String resultJson = json.writeValueAsString(Map.of(
                "jobId", job.id(), "algorithm", algorithm, "metrics", metrics,
                "outputPath", outputPath, "modelPath", modelPath == null ? "" : modelPath,
                "humanReviewRequired", job.jobType().equals("RECEIVABLES_RISK")));
        return new JobResult(resultJson, algorithm, job.parametersJson(), json.writeValueAsString(metrics),
                inputEvidence, modelPath, manifest);
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

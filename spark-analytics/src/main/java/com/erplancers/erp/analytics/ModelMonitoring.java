package com.erplancers.erp.analytics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;

/** Holdout metrics and Population Stability Index (PSI) drift measurements. */
public final class ModelMonitoring {
    private ModelMonitoring() {}

    public record DriftMetric(String feature, double populationStabilityIndex, String status,
                              long baselineNonNullRows, long currentNonNullRows) {}
    public record RegressionMetrics(double rmse, double mae, double r2) {}
    public record ClassificationMetrics(double areaUnderRoc, double weightedPrecision, double weightedRecall) {}

    public static RegressionMetrics regression(Dataset<Row> predictions, String label, String prediction) {
        double rmse = evaluator(predictions, label, prediction, "rmse");
        double mae = evaluator(predictions, label, prediction, "mae");
        double r2 = evaluator(predictions, label, prediction, "r2");
        return new RegressionMetrics(rmse, mae, r2);
    }

    private static double evaluator(Dataset<Row> rows, String label, String prediction, String metric) {
        return new org.apache.spark.ml.evaluation.RegressionEvaluator()
                .setLabelCol(label).setPredictionCol(prediction).setMetricName(metric).evaluate(rows);
    }

    public static ClassificationMetrics classification(Dataset<Row> predictions, String label) {
        double auc = new org.apache.spark.ml.evaluation.BinaryClassificationEvaluator()
                .setLabelCol(label).setRawPredictionCol("rawPrediction").setMetricName("areaUnderROC").evaluate(predictions);
        double precision = new org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator()
                .setLabelCol(label).setPredictionCol("prediction").setMetricName("weightedPrecision").evaluate(predictions);
        double recall = new org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator()
                .setLabelCol(label).setPredictionCol("prediction").setMetricName("weightedRecall").evaluate(predictions);
        return new ClassificationMetrics(auc, precision, recall);
    }

    /**
     * PSI using baseline deciles as fixed bin boundaries. PSI >= 0.25 is a common operational
     * alert heuristic, not a universal statistical proof of drift; validate thresholds per feature.
     */
    public static List<DriftMetric> populationStabilityIndex(
            Dataset<Row> baseline, Dataset<Row> current, List<String> numericFeatures, double alertThreshold) {
        if (numericFeatures == null || numericFeatures.isEmpty()) throw new IllegalArgumentException("numericFeatures is required");
        if (!(alertThreshold > 0)) throw new IllegalArgumentException("alertThreshold must be positive");
        SalesIntelligence.requireColumns(baseline, numericFeatures.toArray(String[]::new));
        SalesIntelligence.requireColumns(current, numericFeatures.toArray(String[]::new));
        List<DriftMetric> result = new ArrayList<>();
        for (String feature : numericFeatures) {
            double[] cuts = baseline.stat().approxQuantile(feature,
                    new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9}, 0.01);
            Dataset<Row> b = baseline.filter(functions.col(feature).isNotNull());
            Dataset<Row> c = current.filter(functions.col(feature).isNotNull());
            long bn = b.count(), cn = c.count();
            if (bn == 0 || cn == 0) {
                result.add(new DriftMetric(feature, Double.NaN, "INSUFFICIENT_DATA", bn, cn));
                continue;
            }
            double psi = 0.0;
            double lower = Double.NEGATIVE_INFINITY;
            for (int i = 0; i <= cuts.length; i++) {
                double upper = i < cuts.length ? cuts[i] : Double.POSITIVE_INFINITY;
                long bc = countBin(b, feature, lower, upper, i == cuts.length);
                long cc = countBin(c, feature, lower, upper, i == cuts.length);
                double bp = Math.max(1e-6, (double) bc / bn);
                double cp = Math.max(1e-6, (double) cc / cn);
                psi += (cp - bp) * Math.log(cp / bp);
                lower = upper;
            }
            result.add(new DriftMetric(feature, psi,
                    psi >= alertThreshold ? "ALERT" : "OK", bn, cn));
        }
        return List.copyOf(result);
    }

    private static long countBin(Dataset<Row> data, String feature, double lower, double upper, boolean last) {
        var value = functions.col(feature);
        var filter = last ? value.geq(lower) : value.geq(lower).and(value.lt(upper));
        if (Double.isInfinite(lower) && lower < 0) filter = last ? value.isNotNull() : value.lt(upper);
        return data.filter(filter).count();
    }
}

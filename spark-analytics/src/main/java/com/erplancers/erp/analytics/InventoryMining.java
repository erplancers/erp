package com.erplancers.erp.analytics;

import java.util.List;
import java.util.Objects;

import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.PipelineStage;
import org.apache.spark.ml.evaluation.RegressionEvaluator;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.ml.regression.LinearRegression;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.functions;

/** Time-aware demand forecasts, supervised stock-out scoring and rolling z-score anomalies. */
public final class InventoryMining {
    private InventoryMining() {}

    public record ForecastResult(PipelineModel model, Dataset<Row> predictions, Dataset<Row> baselineFeatures,
                                 double rmse, double mae, double r2, long trainingRows, long holdoutRows) {}
    public record StockoutResult(PipelineModel model, Dataset<Row> predictions, Dataset<Row> baselineFeatures,
                                 double areaUnderRoc, double weightedPrecision, double weightedRecall,
                                 long trainingRows, long holdoutRows) {}

    public static ForecastResult forecastDemand(
            Dataset<Row> dailyDemand, String product, String warehouse, String day, String quantity,
            String trainThroughDate, String modelPath) throws java.io.IOException {
        SalesIntelligence.requireColumns(dailyDemand, product, warehouse, day, quantity);
        if (trainThroughDate == null || trainThroughDate.isBlank()) throw new IllegalArgumentException("trainThroughDate is required");
        if (modelPath == null || modelPath.isBlank()) throw new IllegalArgumentException("modelPath is required");
        Dataset<Row> features = demandFeatureFrame(dailyDemand, product, warehouse, day, quantity);
        String[] inputs = {"_lag_1", "_lag_7", "_lag_14", "_day_of_week"};
        Dataset<Row> train = features.filter(functions.to_date(functions.col(day))
                .leq(functions.to_date(functions.lit(trainThroughDate))));
        Dataset<Row> test = features.filter(functions.to_date(functions.col(day))
                .gt(functions.to_date(functions.lit(trainThroughDate))));
        long trainCount = train.count(), testCount = test.count();
        if (trainCount < 20 || testCount < 1) {
            throw new IllegalArgumentException("Demand forecasting requires >=20 training rows and a non-empty later-date holdout");
        }
        VectorAssembler assembler = new VectorAssembler().setInputCols(inputs).setOutputCol("features").setHandleInvalid("error");
        LinearRegression regression = new LinearRegression().setFeaturesCol("features").setLabelCol(quantity)
                .setPredictionCol("prediction").setMaxIter(100).setRegParam(0.05).setElasticNetParam(0.2);
        Pipeline pipeline = new Pipeline().setStages(new PipelineStage[]{assembler, regression});
        PipelineModel model = pipeline.fit(train);
        Dataset<Row> predictions = model.transform(test).select(product, warehouse, day, quantity, "prediction");
        double rmse = new RegressionEvaluator().setLabelCol(quantity).setPredictionCol("prediction").setMetricName("rmse").evaluate(predictions);
        double mae = new RegressionEvaluator().setLabelCol(quantity).setPredictionCol("prediction").setMetricName("mae").evaluate(predictions);
        double r2 = new RegressionEvaluator().setLabelCol(quantity).setPredictionCol("prediction").setMetricName("r2").evaluate(predictions);
        model.write().overwrite().save(modelPath);
        return new ForecastResult(model, predictions, SalesIntelligence.selectColumns(train, inputs), rmse, mae, r2, trainCount, testCount);
    }

    /**
     * Requires a historical, labelled training dataset. Each feature must be numeric;
     * label must be 0/1. Production callers should choose a time-based holdout upstream.
     */
    public static StockoutResult trainStockoutRisk(
            Dataset<Row> labelledRows, List<String> featureColumns, String labelColumn,
            double trainFraction, String modelPath) throws java.io.IOException {
        if (featureColumns == null || featureColumns.isEmpty()) throw new IllegalArgumentException("featureColumns is required");
        if (!(trainFraction >= 0.5 && trainFraction < 0.95)) throw new IllegalArgumentException("trainFraction must be in [0.5, 0.95)");
        if (modelPath == null || modelPath.isBlank()) throw new IllegalArgumentException("modelPath is required");
        String[] required = new String[featureColumns.size() + 1];
        for (int i = 0; i < featureColumns.size(); i++) required[i] = featureColumns.get(i);
        required[required.length - 1] = labelColumn;
        SalesIntelligence.requireColumns(labelledRows, required);
        Dataset<Row> clean = labelledRows.select(required[0],
                java.util.Arrays.copyOfRange(required, 1, required.length))
                .na().drop("any", required);
        Dataset<Row>[] split = clean.randomSplit(new double[]{trainFraction, 1.0 - trainFraction}, 20261010L);
        long trainCount = split[0].count(), holdoutCount = split[1].count();
        if (trainCount < 20 || holdoutCount < 1) throw new IllegalArgumentException("Not enough labelled rows for risk model");
        VectorAssembler assembler = new VectorAssembler().setInputCols(featureColumns.toArray(String[]::new))
                .setOutputCol("features").setHandleInvalid("error");
        org.apache.spark.ml.classification.LogisticRegression classifier =
                new org.apache.spark.ml.classification.LogisticRegression()
                        .setFeaturesCol("features").setLabelCol(labelColumn).setMaxIter(100).setRegParam(0.05);
        PipelineModel model = new Pipeline().setStages(new PipelineStage[]{assembler, classifier}).fit(split[0]);
        Dataset<Row> predictions = model.transform(split[1]);
        ModelMonitoring.ClassificationMetrics quality = ModelMonitoring.classification(predictions, labelColumn);
        model.write().overwrite().save(modelPath);
        return new StockoutResult(model, predictions, SalesIntelligence.selectColumns(split[0], featureColumns.toArray(String[]::new)),
                quality.areaUnderRoc(), quality.weightedPrecision(), quality.weightedRecall(), trainCount, holdoutCount);
    }



    /** Preferred production variant: evaluates against a strictly later, date-based holdout. */
    public static StockoutResult trainStockoutRiskTimeOrdered(
            Dataset<Row> labelledRows, List<String> featureColumns, String labelColumn,
            String dateColumn, List<String> identifierColumns, String trainThroughDate, String modelPath) throws java.io.IOException {
        if (featureColumns == null || featureColumns.isEmpty()) throw new IllegalArgumentException("featureColumns is required");
        if (trainThroughDate == null || trainThroughDate.isBlank()) throw new IllegalArgumentException("trainThroughDate is required");
        if (modelPath == null || modelPath.isBlank()) throw new IllegalArgumentException("modelPath is required");
        java.util.LinkedHashSet<String> selectedSet = new java.util.LinkedHashSet<>(featureColumns);
        selectedSet.add(labelColumn);
        selectedSet.add(dateColumn);
        if (identifierColumns != null) selectedSet.addAll(identifierColumns);
        String[] selected = selectedSet.toArray(String[]::new);
        SalesIntelligence.requireColumns(labelledRows, selected);
        Dataset<Row> clean = labelledRows.select(selected[0],
                java.util.Arrays.copyOfRange(selected, 1, selected.length)).na().drop("any", selected);
        Dataset<Row> train = clean.filter(functions.to_date(functions.col(dateColumn))
                .leq(functions.to_date(functions.lit(trainThroughDate))));
        Dataset<Row> holdout = clean.filter(functions.to_date(functions.col(dateColumn))
                .gt(functions.to_date(functions.lit(trainThroughDate))));
        return fitStockout(train, holdout, featureColumns, labelColumn, modelPath);
    }

    private static StockoutResult fitStockout(Dataset<Row> train, Dataset<Row> holdout,
                                               List<String> featureColumns, String labelColumn, String modelPath) throws java.io.IOException {
        long trainCount = train.count(), holdoutCount = holdout.count();
        if (trainCount < 20 || holdoutCount < 10) {
            throw new IllegalArgumentException("Stock-out risk requires >=20 training and >=10 holdout rows");
        }
        VectorAssembler assembler = new VectorAssembler().setInputCols(featureColumns.toArray(String[]::new))
                .setOutputCol("features").setHandleInvalid("error");
        org.apache.spark.ml.classification.LogisticRegression classifier =
                new org.apache.spark.ml.classification.LogisticRegression()
                        .setFeaturesCol("features").setLabelCol(labelColumn).setMaxIter(100).setRegParam(0.05);
        PipelineModel model = new Pipeline().setStages(new PipelineStage[]{assembler, classifier}).fit(train);
        Dataset<Row> rawPredictions = model.transform(holdout);
        ModelMonitoring.ClassificationMetrics quality = ModelMonitoring.classification(rawPredictions, labelColumn);
        Dataset<Row> predictions = rawPredictions
                .withColumn("stockout_risk_score",
                        org.apache.spark.ml.functions.vector_to_array(functions.col("probability"), "float64").getItem(1))
                .withColumn("human_review_required", functions.col("stockout_risk_score").geq(0.65))
                .withColumn("recommended_action", functions.lit("REVIEW_INVENTORY"))
                .drop("features", "rawPrediction", "probability");
        model.write().overwrite().save(modelPath);
        return new StockoutResult(model, predictions, SalesIntelligence.selectColumns(train, featureColumns.toArray(String[]::new)),
                quality.areaUnderRoc(), quality.weightedPrecision(), quality.weightedRecall(), trainCount, holdoutCount);
    }

    /** Deterministic lag-feature transformation reused by training and drift monitoring. */
    public static Dataset<Row> demandFeatureFrame(
            Dataset<Row> dailyDemand, String product, String warehouse, String day, String quantity) {
        SalesIntelligence.requireColumns(dailyDemand, product, warehouse, day, quantity);
        var window = Window.partitionBy(product, warehouse).orderBy(functions.to_date(functions.col(day)));
        return dailyDemand
                .filter(functions.col(product).isNotNull().and(functions.col(warehouse).isNotNull())
                        .and(functions.col(day).isNotNull()).and(functions.col(quantity).isNotNull()))
                .withColumn("_lag_1", functions.lag(functions.col(quantity), 1).over(window))
                .withColumn("_lag_7", functions.lag(functions.col(quantity), 7).over(window))
                .withColumn("_lag_14", functions.lag(functions.col(quantity), 14).over(window))
                .withColumn("_day_of_week", functions.dayofweek(functions.to_date(functions.col(day))).cast("double"))
                .na().drop("any", new String[]{"_lag_1", "_lag_7", "_lag_14"});
    }

    /** Finds unusually high/low demand vs the previous 28 observations for each item/location. */
    public static Dataset<Row> demandAnomalies(
            Dataset<Row> dailyDemand, String product, String warehouse, String day, String quantity,
            double zThreshold) {
        if (!(zThreshold >= 2.0 && zThreshold <= 8.0)) throw new IllegalArgumentException("zThreshold must be between 2 and 8");
        SalesIntelligence.requireColumns(dailyDemand, product, warehouse, day, quantity);
        var window = Window.partitionBy(product, warehouse).orderBy(functions.to_date(functions.col(day))).rowsBetween(-28, -1);
        Dataset<Row> stats = dailyDemand
                .withColumn("baseline_mean", functions.avg(functions.col(quantity)).over(window))
                .withColumn("baseline_stddev", functions.stddev_samp(functions.col(quantity)).over(window))
                .withColumn("anomaly_z_score",
                        functions.when(functions.col("baseline_stddev").gt(0),
                                functions.abs(functions.col(quantity).minus(functions.col("baseline_mean"))
                                        .divide(functions.col("baseline_stddev"))))
                                .otherwise(functions.lit(0.0)));
        return stats.filter(functions.col("baseline_mean").isNotNull()
                .and(functions.col("anomaly_z_score").geq(zThreshold)));
    }
}

package com.erplancers.erp.analytics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.PipelineStage;
import org.apache.spark.ml.classification.GBTClassifier;
import org.apache.spark.ml.classification.LogisticRegression;
import org.apache.spark.ml.classification.RandomForestClassifier;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;

/**
 * Time-ordered churn/retention propensity modelling for an operator-curated labelled view.
 * A churn label must be defined at a real decision date by the data owner; do not derive it
 * from future outcomes or include post-decision fields in featureColumns.
 */
public final class CustomerChurnMining {
    private CustomerChurnMining() {}

    public record Result(PipelineModel model, Dataset<Row> predictions, Dataset<Row> baselineFeatures,
                         double areaUnderRoc, double weightedPrecision, double weightedRecall,
                         long trainingRows, long holdoutRows, String algorithm) {}

    public static Result trainTimeOrdered(
            Dataset<Row> labelledRows, List<String> featureColumns, String labelColumn,
            String dateColumn, List<String> identifierColumns, String trainThroughDate,
            String algorithm, double reviewThreshold, String modelPath) throws java.io.IOException {
        if (featureColumns == null || featureColumns.isEmpty()) throw new IllegalArgumentException("featureColumns is required");
        if (trainThroughDate == null || trainThroughDate.isBlank()) throw new IllegalArgumentException("trainThroughDate is required");
        if (!(reviewThreshold >= 0.0 && reviewThreshold <= 1.0)) throw new IllegalArgumentException("reviewThreshold must be in [0,1]");
        if (modelPath == null || modelPath.isBlank()) throw new IllegalArgumentException("modelPath is required");
        LinkedHashSet<String> selected = new LinkedHashSet<>(featureColumns);
        selected.add(labelColumn);
        selected.add(dateColumn);
        if (identifierColumns != null) selected.addAll(identifierColumns);
        SalesIntelligence.requireColumns(labelledRows, selected.toArray(String[]::new));
        String[] columns = selected.toArray(String[]::new);
        Dataset<Row> clean = labelledRows.select(columns[0],
                java.util.Arrays.copyOfRange(columns, 1, columns.length)).na().drop("any", columns);
        Dataset<Row> train = clean.filter(functions.to_date(functions.col(dateColumn))
                .leq(functions.to_date(functions.lit(trainThroughDate))));
        Dataset<Row> holdout = clean.filter(functions.to_date(functions.col(dateColumn))
                .gt(functions.to_date(functions.lit(trainThroughDate))));
        long trainRows = train.count(), holdoutRows = holdout.count();
        if (trainRows < 50 || holdoutRows < 10) {
            throw new IllegalArgumentException("Churn modelling requires >=50 training and >=10 later-date holdout rows");
        }
        VectorAssembler assembler = new VectorAssembler().setInputCols(featureColumns.toArray(String[]::new))
                .setOutputCol("features").setHandleInvalid("error");
        String selectedAlgorithm = algorithm == null ? "LOGISTIC_REGRESSION" : algorithm.toUpperCase(java.util.Locale.ROOT);
        PipelineStage classifier = switch (selectedAlgorithm) {
            case "LOGISTIC_REGRESSION" -> new LogisticRegression().setFeaturesCol("features")
                    .setLabelCol(labelColumn).setMaxIter(150).setRegParam(0.1).setElasticNetParam(0.25);
            case "RANDOM_FOREST" -> new RandomForestClassifier().setFeaturesCol("features")
                    .setLabelCol(labelColumn).setNumTrees(100).setMaxDepth(8).setSeed(421337L);
            case "GRADIENT_BOOSTED_TREES" -> new GBTClassifier().setFeaturesCol("features")
                    .setLabelCol(labelColumn).setMaxIter(80).setMaxDepth(5).setSeed(421337L);
            default -> throw new IllegalArgumentException("algorithm must be LOGISTIC_REGRESSION, RANDOM_FOREST, or GRADIENT_BOOSTED_TREES");
        };
        PipelineModel model = new Pipeline().setStages(new PipelineStage[]{assembler, classifier}).fit(train);
        Dataset<Row> raw = model.transform(holdout);
        ModelMonitoring.ClassificationMetrics quality = ModelMonitoring.classification(raw, labelColumn);
        Dataset<Row> predictions = raw
                .withColumn("churn_risk_score", org.apache.spark.ml.functions.vector_to_array(
                        functions.col("probability"), "float64").getItem(1))
                .withColumn("human_review_required", functions.col("churn_risk_score").geq(functions.lit(reviewThreshold)))
                .withColumn("recommended_action", functions.lit("RETENTION_REVIEW_ONLY"))
                .drop("features", "rawPrediction", "probability");
        model.write().overwrite().save(modelPath);
        return new Result(model, predictions, SalesIntelligence.selectColumns(train, featureColumns.toArray(String[]::new)),
                quality.areaUnderRoc(), quality.weightedPrecision(), quality.weightedRecall(),
                trainRows, holdoutRows, selectedAlgorithm);
    }
}

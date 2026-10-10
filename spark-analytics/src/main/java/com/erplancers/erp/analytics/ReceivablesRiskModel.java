package com.erplancers.erp.analytics;

import java.util.List;

import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.PipelineStage;
import org.apache.spark.ml.classification.LogisticRegression;
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.ml.functions;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.lit;

/**
 * Overdue-payment risk scoring. Scores are decision support only; never automatically decline,
 * block or change credit terms without an approved, human-reviewed policy.
 */
public final class ReceivablesRiskModel {
    private ReceivablesRiskModel() {}

    public record Result(PipelineModel model, Dataset<Row> reviewQueue, Dataset<Row> baselineFeatures,
                         double areaUnderRoc, double weightedPrecision, double weightedRecall,
                         long trainingRows, long validationRows, double reviewThreshold) {}

    public static Result train(
            Dataset<Row> historicalInvoices, List<String> numericFeatures,
            String overdueLabel, double reviewThreshold, double trainFraction,
            String versionedModelPath) {
        if (numericFeatures == null || numericFeatures.isEmpty()) throw new IllegalArgumentException("numericFeatures is required");
        if (!(reviewThreshold >= 0.0 && reviewThreshold <= 1.0)) throw new IllegalArgumentException("reviewThreshold must be in [0,1]");
        if (!(trainFraction >= 0.5 && trainFraction < 0.95)) throw new IllegalArgumentException("trainFraction must be in [0.5,0.95)");
        if (versionedModelPath == null || versionedModelPath.isBlank()) throw new IllegalArgumentException("versionedModelPath is required");
        String[] required = new String[numericFeatures.size() + 1];
        for (int i = 0; i < numericFeatures.size(); i++) required[i] = numericFeatures.get(i);
        required[required.length - 1] = overdueLabel;
        SalesIntelligence.requireColumns(historicalInvoices, required);
        Dataset<Row> clean = historicalInvoices.select(required[0],
                java.util.Arrays.copyOfRange(required, 1, required.length))
                .na().drop("any", required);
        Dataset<Row>[] split = clean.randomSplit(new double[]{trainFraction, 1.0 - trainFraction}, 421337L);
        long trainRows = split[0].count(), validationRows = split[1].count();
        if (trainRows < 50 || validationRows < 10) {
            throw new IllegalArgumentException("Need at least 50 training and 10 validation invoices");
        }
        VectorAssembler assembler = new VectorAssembler().setInputCols(numericFeatures.toArray(String[]::new))
                .setOutputCol("features").setHandleInvalid("error");
        LogisticRegression lr = new LogisticRegression().setFeaturesCol("features").setLabelCol(overdueLabel)
                .setMaxIter(150).setRegParam(0.1).setElasticNetParam(0.25);
        PipelineModel model = new Pipeline().setStages(new PipelineStage[]{assembler, lr}).fit(split[0]);
        Dataset<Row> predictions = model.transform(split[1]);
        ModelMonitoring.ClassificationMetrics quality = ModelMonitoring.classification(predictions, overdueLabel);
        Dataset<Row> reviewQueue = humanReviewQueue(predictions, reviewThreshold);
        model.write().overwrite().save(versionedModelPath);
        return new Result(model, reviewQueue, split[0].select(numericFeatures.toArray(String[]::new)),
                quality.areaUnderRoc(), quality.weightedPrecision(), quality.weightedRecall(),
                trainRows, validationRows, reviewThreshold);
    }

    /** Preferred production variant; separates training and validation by a decision-time cutoff. */
    public static Result trainTimeOrdered(
            Dataset<Row> historicalInvoices, List<String> numericFeatures, String overdueLabel,
            String decisionDateColumn, String trainThroughDate, double reviewThreshold,
            String versionedModelPath) {
        if (numericFeatures == null || numericFeatures.isEmpty()) throw new IllegalArgumentException("numericFeatures is required");
        if (trainThroughDate == null || trainThroughDate.isBlank()) throw new IllegalArgumentException("trainThroughDate is required");
        if (!(reviewThreshold >= 0.0 && reviewThreshold <= 1.0)) throw new IllegalArgumentException("reviewThreshold must be in [0,1]");
        if (versionedModelPath == null || versionedModelPath.isBlank()) throw new IllegalArgumentException("versionedModelPath is required");
        String[] selected = new String[numericFeatures.size() + 2];
        for (int i = 0; i < numericFeatures.size(); i++) selected[i] = numericFeatures.get(i);
        selected[numericFeatures.size()] = overdueLabel;
        selected[numericFeatures.size() + 1] = decisionDateColumn;
        SalesIntelligence.requireColumns(historicalInvoices, selected);
        Dataset<Row> clean = historicalInvoices.select(selected[0],
                java.util.Arrays.copyOfRange(selected, 1, selected.length)).na().drop("any", selected);
        Dataset<Row> train = clean.filter(org.apache.spark.sql.functions.to_date(col(decisionDateColumn))
                .leq(org.apache.spark.sql.functions.to_date(lit(trainThroughDate))));
        Dataset<Row> validation = clean.filter(org.apache.spark.sql.functions.to_date(col(decisionDateColumn))
                .gt(org.apache.spark.sql.functions.to_date(lit(trainThroughDate))));
        return fitTimeOrdered(train, validation, numericFeatures, overdueLabel, reviewThreshold, versionedModelPath);
    }

    private static Result fitTimeOrdered(Dataset<Row> train, Dataset<Row> validation,
                                         List<String> numericFeatures, String overdueLabel,
                                         double reviewThreshold, String modelPath) {
        long trainRows = train.count(), validationRows = validation.count();
        if (trainRows < 50 || validationRows < 10) {
            throw new IllegalArgumentException("Need at least 50 training and 10 later-date validation invoices");
        }
        VectorAssembler assembler = new VectorAssembler().setInputCols(numericFeatures.toArray(String[]::new))
                .setOutputCol("features").setHandleInvalid("error");
        LogisticRegression lr = new LogisticRegression().setFeaturesCol("features").setLabelCol(overdueLabel)
                .setMaxIter(150).setRegParam(0.1).setElasticNetParam(0.25);
        PipelineModel model = new Pipeline().setStages(new PipelineStage[]{assembler, lr}).fit(train);
        Dataset<Row> predictions = model.transform(validation);
        ModelMonitoring.ClassificationMetrics quality = ModelMonitoring.classification(predictions, overdueLabel);
        Dataset<Row> reviewQueue = humanReviewQueue(predictions, reviewThreshold);
        model.write().overwrite().save(modelPath);
        return new Result(model, reviewQueue, train.select(numericFeatures.toArray(String[]::new)),
                quality.areaUnderRoc(), quality.weightedPrecision(), quality.weightedRecall(),
                trainRows, validationRows, reviewThreshold);
    }

    private static Dataset<Row> humanReviewQueue(Dataset<Row> predictions, double reviewThreshold) {
        return predictions
                .withColumn("overdue_risk_score", org.apache.spark.ml.functions.vector_to_array(col("probability")).getItem(1))
                .withColumn("human_review_required", col("overdue_risk_score").geq(lit(reviewThreshold)))
                .withColumn("recommended_action", lit("HUMAN_REVIEW_ONLY"))
                .drop("features", "rawPrediction", "probability");
    }

}

package com.erplancers.erp.analytics;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.spark.ml.clustering.KMeans;
import org.apache.spark.ml.clustering.KMeansModel;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;

/**
 * Example Spark ML pipeline for customer segmentation.
 * Input must contain a stable customer key and numeric, privacy-reviewed features.
 */
public final class CustomerSegmentationJob {
    private CustomerSegmentationJob() {}

    public static MiningResult run(
            Dataset<Row> input,
            String customerIdColumn,
            List<String> featureColumns,
            int clusters,
            int maxIterations,
            String modelOutputPath,
            String resultOutputPath) {

        if (input == null) throw new IllegalArgumentException("input is required");
        if (featureColumns == null || featureColumns.isEmpty()) {
            throw new IllegalArgumentException("At least one feature column is required");
        }
        if (clusters < 2) throw new IllegalArgumentException("clusters must be >= 2");
        if (maxIterations < 1) throw new IllegalArgumentException("maxIterations must be >= 1");
        if (modelOutputPath == null || modelOutputPath.isBlank()) {
            throw new IllegalArgumentException("modelOutputPath is required");
        }
        if (resultOutputPath == null || resultOutputPath.isBlank()) {
            throw new IllegalArgumentException("resultOutputPath is required");
        }

        String[] requiredColumns = new String[featureColumns.size() + 1];
        requiredColumns[0] = customerIdColumn;
        for (int i = 0; i < featureColumns.size(); i++) {
            requiredColumns[i + 1] = featureColumns.get(i);
        }
        for (String column : requiredColumns) {
            if (column == null || column.isBlank()
                    || !java.util.Arrays.asList(input.columns()).contains(column)) {
                throw new IllegalArgumentException("Missing input column: " + column);
            }
        }

        Dataset<Row> clean = input.select(requiredColumns)
                .na().drop("any", requiredColumns);
        long rows = clean.count();
        if (rows < clusters) {
            throw new IllegalArgumentException("Input row count must be >= cluster count");
        }

        VectorAssembler assembler = new VectorAssembler()
                .setInputCols(featureColumns.toArray(String[]::new))
                .setOutputCol("features")
                .setHandleInvalid("error");
        Dataset<Row> vectors = assembler.transform(clean);

        KMeans algorithm = new KMeans()
                .setK(clusters)
                .setMaxIter(maxIterations)
                .setSeed(42L)
                .setFeaturesCol("features")
                .setPredictionCol("segment");
        KMeansModel model = algorithm.fit(vectors);
        Dataset<Row> scored = model.transform(vectors).select(customerIdColumn, "segment");

        // Save atomically at the storage layer where supported; use versioned paths in production.
        model.write().overwrite().save(modelOutputPath);
        scored.write().mode(SaveMode.Overwrite).parquet(resultOutputPath);

        List<Map<String, Object>> summaries = scored.groupBy("segment")
                .count().orderBy("segment").collectAsList().stream()
                .map(row -> Map.<String, Object>of(
                        "segment", row.getInt(0),
                        "customers", row.getLong(1)))
                .collect(Collectors.toList());

        return new MiningResult("k-means-customer-segmentation", rows,
                summaries.size(), summaries, modelOutputPath);
    }
}

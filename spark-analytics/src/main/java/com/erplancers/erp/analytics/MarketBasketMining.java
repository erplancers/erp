package com.erplancers.erp.analytics;

import org.apache.spark.ml.fpm.FPGrowth;
import org.apache.spark.ml.fpm.FPGrowthModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

/** Association-rule mining for cross-selling; transaction IDs must be stable and tenant-scoped upstream. */
public final class MarketBasketMining {
    private MarketBasketMining() {}

    public record Result(FPGrowthModel model, Dataset<Row> frequentItemsets,
                         Dataset<Row> associationRules, long transactionCount) {}

    public static Result mine(
            Dataset<Row> transactionItems, String transactionId, String productId,
            double minSupport, double minConfidence, String modelPath) {
        SalesIntelligence.requireColumns(transactionItems, transactionId, productId);
        if (!(minSupport > 0.0 && minSupport <= 1.0)) throw new IllegalArgumentException("minSupport must be in (0,1]");
        if (!(minConfidence > 0.0 && minConfidence <= 1.0)) throw new IllegalArgumentException("minConfidence must be in (0,1]");
        if (modelPath == null || modelPath.isBlank()) throw new IllegalArgumentException("modelPath is required");
        Dataset<Row> transactions = transactionItems
                .filter(org.apache.spark.sql.functions.col(transactionId).isNotNull()
                        .and(org.apache.spark.sql.functions.col(productId).isNotNull()))
                .groupBy(transactionId)
                .agg(org.apache.spark.sql.functions.collect_set(productId).alias("items"))
                .filter(org.apache.spark.sql.functions.size(org.apache.spark.sql.functions.col("items")).gt(0));
        long count = transactions.count();
        if (count < 2) throw new IllegalArgumentException("At least two non-empty transactions are required");
        FPGrowthModel model = new FPGrowth().setItemsCol("items").setMinSupport(minSupport)
                .setMinConfidence(minConfidence).setNumPartitions(Math.max(1, transactionItems.sparkSession().sparkContext().defaultParallelism()))
                .fit(transactions);
        model.write().overwrite().save(modelPath);
        return new Result(model, model.freqItemsets(), model.associationRules(), count);
    }
}

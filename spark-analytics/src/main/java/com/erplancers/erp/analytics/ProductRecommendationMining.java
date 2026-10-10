package com.erplancers.erp.analytics;

import java.io.IOException;

import org.apache.spark.ml.recommendation.ALS;
import org.apache.spark.ml.recommendation.ALSModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.functions;

/**
 * Implicit-feedback item recommendations for order baskets.
 * A transaction is the recommendation context and products are the recommended items;
 * this is not customer-level personalization unless the source is curated to customer-item events.
 */
public final class ProductRecommendationMining {
    private ProductRecommendationMining() {}

    public record Result(Dataset<Row> recommendations, long interactionCount,
                         long transactionCount, long productCount, String modelPath) {}

    public static Result recommendForBaskets(Dataset<Row> orderLines, String transactionColumn,
            String productColumn, int topN, int rank, int maxIterations, double regularization,
            String modelPath) throws IOException {
        if (topN < 1 || topN > 100) throw new IllegalArgumentException("topN must be in [1,100]");
        if (rank < 2 || rank > 128) throw new IllegalArgumentException("rank must be in [2,128]");
        if (maxIterations < 1 || maxIterations > 100) throw new IllegalArgumentException("maxIterations must be in [1,100]");
        if (!(regularization >= 0.0 && regularization <= 10.0)) throw new IllegalArgumentException("regularization must be in [0,10]");
        if (modelPath == null || modelPath.isBlank()) throw new IllegalArgumentException("modelPath is required");
        SalesIntelligence.requireColumns(orderLines, transactionColumn, productColumn);

        Dataset<Row> clean = orderLines.select(
                functions.trim(functions.col(transactionColumn).cast("string")).alias("transaction_key"),
                functions.trim(functions.col(productColumn).cast("string")).alias("product_key"))
                .filter(functions.col("transaction_key").isNotNull()
                        .and(functions.length(functions.col("transaction_key")).gt(0))
                        .and(functions.col("product_key").isNotNull())
                        .and(functions.length(functions.col("product_key")).gt(0))
                        .and(functions.length(functions.col("product_key")).gt(0)));
        Dataset<Row> interactions = clean.groupBy("transaction_key", "product_key")
                .agg(functions.count(functions.lit(1)).cast("float").alias("rating"));
        long interactionCount = interactions.count();
        long transactionCount = interactions.select("transaction_key").distinct().count();
        long productCount = interactions.select("product_key").distinct().count();
        if (interactionCount < 20 || transactionCount < 2 || productCount < 2) {
            throw new IllegalArgumentException("Product recommendations require >=20 distinct basket-product interactions, >=2 baskets and >=2 products");
        }

        Dataset<Row> transactionMap = interactions.select("transaction_key").distinct()
                .withColumn("userId", functions.row_number().over(Window.orderBy(functions.col("transaction_key"))).minus(1).cast("int"));
        Dataset<Row> productMap = interactions.select("product_key").distinct()
                .withColumn("itemId", functions.row_number().over(Window.orderBy(functions.col("product_key"))).minus(1).cast("int"));
        Dataset<Row> training = interactions.join(transactionMap, "transaction_key")
                .join(productMap, "product_key")
                .select("userId", "itemId", "rating");

        ALS estimator = new ALS().setUserCol("userId").setItemCol("itemId").setRatingCol("rating")
                .setImplicitPrefs(true).setRank(rank).setMaxIter(maxIterations)
                .setRegParam(regularization).setColdStartStrategy("drop").setSeed(421337L);
        ALSModel model = estimator.fit(training);
        model.write().overwrite().save(modelPath);

        Dataset<Row> expanded = model.recommendForAllUsers(topN)
                .withColumn("recommendation", functions.explode(functions.col("recommendations")))
                .select("userId", functions.col("recommendation.itemId").alias("itemId"),
                        functions.col("recommendation.rating").alias("predicted_preference"));
        Dataset<Row> recommendations = expanded.join(transactionMap, "userId")
                .join(productMap, "itemId")
                .withColumn("recommendation_rank",
                        functions.row_number().over(Window.partitionBy("transaction_key")
                                .orderBy(functions.col("predicted_preference").desc())))
                .select(functions.col("transaction_key").alias("transaction_id"),
                        functions.col("product_key").alias("recommended_product_id"),
                        "predicted_preference", "recommendation_rank")
                .filter(functions.col("recommendation_rank").leq(topN));
        return new Result(recommendations, interactionCount, transactionCount, productCount, modelPath);
    }
}

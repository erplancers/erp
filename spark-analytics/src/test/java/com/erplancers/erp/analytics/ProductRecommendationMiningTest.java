package com.erplancers.erp.analytics;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProductRecommendationMiningTest {
    @Test
    void rejectsInvalidRecommendationCountBeforeStartingSparkWork() {
        assertThrows(IllegalArgumentException.class, () ->
                ProductRecommendationMining.recommendForBaskets(null, "transaction_id", "product_id",
                        0, 16, 15, 0.1, "file:/tmp/als-model"));
        assertThrows(IllegalArgumentException.class, () ->
                ProductRecommendationMining.recommendForBaskets(null, "transaction_id", "product_id",
                        101, 16, 15, 0.1, "file:/tmp/als-model"));
    }

    @Test
    void rejectsUnsafeModelHyperparameters() {
        assertThrows(IllegalArgumentException.class, () ->
                ProductRecommendationMining.recommendForBaskets(null, "transaction_id", "product_id",
                        10, 1, 15, 0.1, "file:/tmp/als-model"));
        assertThrows(IllegalArgumentException.class, () ->
                ProductRecommendationMining.recommendForBaskets(null, "transaction_id", "product_id",
                        10, 16, 101, 0.1, "file:/tmp/als-model"));
        assertThrows(IllegalArgumentException.class, () ->
                ProductRecommendationMining.recommendForBaskets(null, "transaction_id", "product_id",
                        10, 16, 15, Double.NaN, "file:/tmp/als-model"));
    }
}

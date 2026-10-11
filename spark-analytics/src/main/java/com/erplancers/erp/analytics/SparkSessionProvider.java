package com.erplancers.erp.analytics;

import org.apache.spark.sql.SparkSession;

/**
 * Creates the Spark session for either the embedded ERP engine or standalone worker.
 */
public final class SparkSessionProvider implements AutoCloseable {
    private final SparkSession session;

    public SparkSessionProvider(String appName, String master) {
        String resolvedMaster = (master == null || master.isBlank())
                ? System.getenv().getOrDefault("SPARK_MASTER", "local[*]")
                : master;
        this.session = SparkSession.builder()
                .appName(requireText(appName, "appName"))
                .master(resolvedMaster)
                .config("spark.sql.session.timeZone", "UTC")
                .config("spark.sql.adaptive.enabled", "true")
                .config("spark.sql.execution.arrow.pyspark.enabled", "false")
                .getOrCreate();
    }

    public SparkSession session() {
        return session;
    }

    @Override
    public void close() {
        session.stop();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

package com.erplancers.erp.analytics;

import org.apache.spark.sql.SparkSession;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Production worker entry point. Run one or more replicas; PostgreSQL leases coordinate claims. */
public final class SparkAnalyticsWorkerMain {
    private SparkAnalyticsWorkerMain() {}

    public static void main(String[] args) throws Exception {
        String metadataUrl = requiredEnv("ANALYTICS_METADATA_JDBC_URL");
        String metadataUser = requiredEnv("ANALYTICS_METADATA_USER");
        String metadataPassword = requiredEnv("ANALYTICS_METADATA_PASSWORD");
        String readUrl = requiredEnv("ANALYTICS_READ_JDBC_URL");
        String readUser = requiredEnv("ANALYTICS_READ_USER");
        String readPassword = requiredEnv("ANALYTICS_READ_PASSWORD");
        Path catalogFile = Path.of(requiredEnv("ANALYTICS_DATASETS_CONFIG"));
        String artifacts = requiredEnv("ANALYTICS_ARTIFACT_URI");
        String workerId = System.getenv().getOrDefault("ANALYTICS_WORKER_ID",
                java.net.InetAddress.getLocalHost().getHostName() + "-" + ProcessHandle.current().pid());
        int idleMs = Integer.parseInt(System.getenv().getOrDefault("ANALYTICS_IDLE_POLL_MS", "1000"));
        int leaseSeconds = Integer.parseInt(System.getenv().getOrDefault("ANALYTICS_JOB_LEASE_SECONDS", "900"));
        if (idleMs < 250 || idleMs > 60_000) throw new IllegalArgumentException("ANALYTICS_IDLE_POLL_MS must be 250..60000");

        AnalyticsDatasetCatalog catalog = AnalyticsDatasetCatalog.load(catalogFile);
        AnalyticsJobStore store = new AnalyticsJobStore(metadataUrl, metadataUser, metadataPassword);
        AtomicBoolean running = new AtomicBoolean(true);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> running.set(false), "analytics-worker-shutdown"));

        String master = System.getenv().getOrDefault("SPARK_MASTER", "local[*]");
        try (SparkSessionProvider provider = new SparkSessionProvider("erplancers-analytics-" + workerId, master)) {
            SparkSession spark = provider.session();
            spark.sparkContext().setLogLevel(System.getenv().getOrDefault("SPARK_LOG_LEVEL", "WARN"));
            ScopedPostgresReader reader = new ScopedPostgresReader(spark, readUrl, readUser, readPassword);
            AnalyticsJobWorker worker = new AnalyticsJobWorker(store,
                    new SparkAnalyticsJobProcessor(catalog, reader, artifacts), workerId, leaseSeconds);
            while (running.get()) {
                try {
                    if (!worker.runOne()) Thread.sleep(idleMs);
                } catch (SQLException databaseError) {
                    System.err.println("Analytics queue database error: " + databaseError.getMessage());
                    Thread.sleep(Math.min(idleMs * 5L, 30_000L));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    running.set(false);
                } catch (RuntimeException invalidJob) {
                    System.err.println("Analytics worker loop error: " + invalidJob.getMessage());
                    Thread.sleep(Math.min(idleMs * 2L, 10_000L));
                }
            }
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Required environment variable is missing: " + name);
        return value;
    }
}

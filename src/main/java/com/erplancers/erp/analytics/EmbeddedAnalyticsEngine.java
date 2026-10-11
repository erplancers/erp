package com.erplancers.erp.analytics;

import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the Spark BI/ML engine in the ERP backend JVM on a managed lifecycle thread.
 * HTTP request threads only enqueue jobs; PostgreSQL leases remain the source of truth.
 */
@Component
@ConditionalOnProperty(prefix = "analytics.engine", name = "enabled", havingValue = "true")
public class EmbeddedAnalyticsEngine implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(EmbeddedAnalyticsEngine.class);

    private final Environment environment;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread engineThread;
    private volatile String state = "STOPPED";

    public EmbeddedAnalyticsEngine(Environment environment) {
        this.environment = environment;
    }

    @Override
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) return;
        state = "STARTING";
        engineThread = new Thread(this::runEngine, "erp-embedded-analytics");
        engineThread.setDaemon(true);
        engineThread.start();
        log.info("Embedded Spark analytics engine starting inside ERP backend");
    }

    private void runEngine() {
        try {
            String metadataUrl = required("ANALYTICS_METADATA_JDBC_URL",
                    environment.getProperty("spring.datasource.url"));
            String metadataUser = required("ANALYTICS_METADATA_USER",
                    environment.getProperty("spring.datasource.username"));
            String metadataPassword = required("ANALYTICS_METADATA_PASSWORD",
                    environment.getProperty("spring.datasource.password"));
            String readUrl = required("ANALYTICS_READ_JDBC_URL", metadataUrl);
            String readUser = required("ANALYTICS_READ_USER", metadataUser);
            String readPassword = required("ANALYTICS_READ_PASSWORD", metadataPassword);
            Path catalogFile = Path.of(required("ANALYTICS_DATASETS_CONFIG", "./deploy/analytics-datasets.properties"));
            String artifactUri = required("ANALYTICS_ARTIFACT_URI", "file:./var/erplancers/analytics");
            String workerId = required("ANALYTICS_WORKER_ID",
                    java.net.InetAddress.getLocalHost().getHostName() + "-" + ProcessHandle.current().pid());
            int idleMs = Integer.parseInt(required("ANALYTICS_IDLE_POLL_MS", "1000"));
            int leaseSeconds = Integer.parseInt(required("ANALYTICS_JOB_LEASE_SECONDS", "900"));
            if (idleMs < 250 || idleMs > 60_000) {
                throw new IllegalArgumentException("ANALYTICS_IDLE_POLL_MS must be 250..60000");
            }
            if (leaseSeconds < 60 || leaseSeconds > 7200) {
                throw new IllegalArgumentException("ANALYTICS_JOB_LEASE_SECONDS must be 60..7200");
            }

            AnalyticsDatasetCatalog catalog = AnalyticsDatasetCatalog.load(catalogFile);
            AnalyticsJobStore store = new AnalyticsJobStore(metadataUrl, metadataUser, metadataPassword);
            String master = required("SPARK_MASTER", "local[*]");
            try (SparkSessionProvider provider = new SparkSessionProvider("erplancers-embedded-analytics-" + workerId, master);
                 KafkaJobWakeup wakeup = KafkaJobWakeup.fromEnvironment()) {
                SparkSession spark = provider.session();
                spark.sparkContext().setLogLevel(required("SPARK_LOG_LEVEL", "WARN"));
                ScopedPostgresReader reader = new ScopedPostgresReader(spark, readUrl, readUser, readPassword);
                AnalyticsJobWorker worker = new AnalyticsJobWorker(store,
                        new SparkAnalyticsJobProcessor(catalog, reader, store, artifactUri),
                        workerId, leaseSeconds, jobId -> spark.sparkContext().cancelJobGroup(jobId));
                state = "RUNNING";
                log.info("Embedded analytics engine ready; datasets={}, master={}, workerId={}",
                        catalog.keys(), master, workerId);
                while (running.get()) {
                    try {
                        if (!worker.runOne()) wakeup.await(idleMs);
                    } catch (SQLException databaseError) {
                        log.error("Embedded analytics queue database error; retrying", databaseError);
                        Thread.sleep(Math.min(idleMs * 5L, 30_000L));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        running.set(false);
                    } catch (RuntimeException invalidJob) {
                        log.error("Embedded analytics worker loop error; continuing", invalidJob);
                        Thread.sleep(Math.min(idleMs * 2L, 10_000L));
                    }
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            running.set(false);
        } catch (Exception startupFailure) {
            state = "FAILED";
            log.error("Embedded analytics engine failed to initialize; ERP API remains available, check analytics configuration", startupFailure);
            running.set(false);
            return;
        } finally {
            if (!"FAILED".equals(state)) state = "STOPPED";
        }
    }

    private String required(String name, String fallback) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) value = fallback;
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required analytics setting is missing: " + name);
        }
        return value.trim();
    }

    public String engineState() {
        return state;
    }

    @Override
    public synchronized void stop() {
        running.set(false);
        Thread thread = engineThread;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(30_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (!"FAILED".equals(state)) state = "STOPPED";
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}

package com.erplancers.erp.analytics;

import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Single-iteration worker loop; run multiple replicas safely using PostgreSQL row leases. */
public final class AnalyticsJobWorker {
    private final AnalyticsJobStore store;
    private final AnalyticsJobHandler handler;
    private final String workerId;
    private final int leaseSeconds;
    private final Consumer<String> cancelSparkJobGroup;

    public AnalyticsJobWorker(AnalyticsJobStore store, AnalyticsJobHandler handler, String workerId,
                              int leaseSeconds, Consumer<String> cancelSparkJobGroup) {
        this.store = Objects.requireNonNull(store);
        this.handler = Objects.requireNonNull(handler);
        this.workerId = Objects.requireNonNull(workerId);
        this.leaseSeconds = leaseSeconds;
        this.cancelSparkJobGroup = cancelSparkJobGroup == null ? ignored -> {} : cancelSparkJobGroup;
    }

    /** @return true if a job was claimed and processed. */
    public boolean runOne() throws SQLException {
        AnalyticsJobStore.Job job = store.claimNext(workerId, leaseSeconds);
        if (job == null) return false;
        AtomicBoolean leaseLost = new AtomicBoolean(false);
        Thread processingThread = Thread.currentThread();
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "analytics-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        long heartbeatSeconds = Math.min(30L, Math.max(5L, leaseSeconds / 3L));
        ScheduledFuture<?> beat = heartbeat.scheduleAtFixedRate(() -> {
            try {
                if (!store.renewLease(job, leaseSeconds)) {
                    leaseLost.set(true);
                    cancelSparkJobGroup.accept(job.id());
                    processingThread.interrupt();
                } else if (store.cancellationRequested(job)) {
                    cancelSparkJobGroup.accept(job.id());
                }
            } catch (Exception failure) {
                leaseLost.set(true);
                cancelSparkJobGroup.accept(job.id());
                processingThread.interrupt();
            }
        }, heartbeatSeconds, heartbeatSeconds, TimeUnit.SECONDS);
        AnalyticsJobHandler.JobExecutionContext context = new AnalyticsJobHandler.JobExecutionContext() {
            @Override public boolean cancellationRequested() throws Exception {
                return leaseLost.get() || store.cancellationRequested(job);
            }
            @Override public void checkCancellation() throws Exception {
                if (leaseLost.get()) throw new JobCancelledException("Worker lost its database lease");
                if (store.cancellationRequested(job)) throw new JobCancelledException("Cancellation requested or lease lost");
            }
        };
        try {
            context.checkCancellation();
            AnalyticsJobHandler.JobResult result = handler.execute(job, context);
            context.checkCancellation();
            for (AnalyticsJobHandler.DriftObservation drift : result.driftObservations()) store.recordDrift(job, drift);
            store.recordExperiment(job, result.algorithm(), result.parametersJson(),
                    result.metricsJson(), result.inputSnapshot(), "SUCCEEDED");
            if (result.artifactUri() != null && !result.artifactUri().isBlank()) {
                store.registerCandidate(job, job.jobType().toLowerCase(java.util.Locale.ROOT),
                        result.artifactUri(), result.manifestJson(), result.metricsJson());
            }
            store.complete(job, workerId, result.resultJson());
        } catch (JobCancelledException cancelled) {
            store.fail(job, workerId, cancelled, false);
        } catch (Exception failure) {
            try {
                store.recordExperiment(job, job.jobType(), job.parametersJson(),
                        "{\"executionFailure\":true}", null, "FAILED_ATTEMPT");
            } catch (SQLException trackingFailure) {
                failure.addSuppressed(trackingFailure);
            }
            boolean retryable = !(failure instanceof IllegalArgumentException
                    || failure instanceof SecurityException
                    || failure instanceof ClassCastException
                    || failure instanceof UnsupportedOperationException);
            store.fail(job, workerId, failure, retryable);
        } finally {
            beat.cancel(true);
            heartbeat.shutdownNow();
            Thread.interrupted();
        }
        return true;
    }

    public static final class JobCancelledException extends Exception {
        public JobCancelledException(String message) { super(message); }
    }
}

package com.erplancers.erp.analytics;

import java.sql.SQLException;
import java.util.Objects;

/** Single-iteration worker loop; run multiple replicas safely using PostgreSQL row leases. */
public final class AnalyticsJobWorker {
    private final AnalyticsJobStore store;
    private final AnalyticsJobHandler handler;
    private final String workerId;
    private final int leaseSeconds;

    public AnalyticsJobWorker(AnalyticsJobStore store, AnalyticsJobHandler handler, String workerId, int leaseSeconds) {
        this.store = Objects.requireNonNull(store);
        this.handler = Objects.requireNonNull(handler);
        this.workerId = Objects.requireNonNull(workerId);
        this.leaseSeconds = leaseSeconds;
    }

    /** @return true if a job was claimed and processed. */
    public boolean runOne() throws SQLException {
        AnalyticsJobStore.Job job = store.claimNext(workerId, leaseSeconds);
        if (job == null) return false;
        AnalyticsJobHandler.JobExecutionContext context = new AnalyticsJobHandler.JobExecutionContext() {
            @Override public boolean cancellationRequested() throws Exception { return store.cancellationRequested(job); }
            @Override public void checkCancellation() throws Exception {
                if (cancellationRequested()) throw new JobCancelledException("Cancellation requested");
            }
        };
        try {
            context.checkCancellation();
            AnalyticsJobHandler.JobResult result = handler.execute(job, context);
            context.checkCancellation();
            store.recordExperiment(job, result.algorithm(), result.parametersJson(),
                    result.metricsJson(), result.inputSnapshot(), "SUCCEEDED");
            for (AnalyticsJobHandler.DriftObservation drift : result.driftObservations()) {
                store.recordDrift(job, drift);
            }
            if (result.artifactUri() != null && !result.artifactUri().isBlank()) {
                store.registerCandidate(job, job.jobType().toLowerCase(java.util.Locale.ROOT),
                        result.artifactUri(), result.manifestJson(), result.metricsJson());
            }
            store.complete(job, workerId, result.resultJson());
        } catch (JobCancelledException cancelled) {
            store.fail(job, workerId, cancelled);
        } catch (Exception failure) {
            try {
                store.recordExperiment(job, job.jobType(), job.parametersJson(),
                        "{\"executionFailure\":true}", null, "FAILED_ATTEMPT");
            } catch (SQLException trackingFailure) { failure.addSuppressed(trackingFailure); }
            store.fail(job, workerId, failure);
        }
        return true;
    }

    public static final class JobCancelledException extends Exception {
        public JobCancelledException(String message) { super(message); }
    }
}

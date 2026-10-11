package com.erplancers.erp.analytics;

/** Pluggable job execution boundary. Implementations must honor cancellation and scope grants. */
public interface AnalyticsJobHandler {
    JobResult execute(AnalyticsJobStore.Job job, JobExecutionContext context) throws Exception;

    record JobResult(String resultJson, String algorithm, String parametersJson,
                     String metricsJson, String inputSnapshot, String artifactUri,
                     String manifestJson, java.util.List<DriftObservation> driftObservations) {
        public JobResult {
            driftObservations = driftObservations == null ? java.util.List.of() : java.util.List.copyOf(driftObservations);
        }
    }

    record DriftObservation(String modelName, int modelVersion, String feature, double psi,
                            String status, long baselineRows, long currentRows,
                            String baselineSnapshot, String currentSnapshot) {}

    interface JobExecutionContext {
        boolean cancellationRequested() throws Exception;
        void checkCancellation() throws Exception;
    }
}

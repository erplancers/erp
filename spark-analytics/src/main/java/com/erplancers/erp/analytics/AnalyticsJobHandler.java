package com.erplancers.erp.analytics;

/** Pluggable job execution boundary. Implementations must honor cancellation and scope grants. */
public interface AnalyticsJobHandler {
    JobResult execute(AnalyticsJobStore.Job job, JobExecutionContext context) throws Exception;

    record JobResult(String resultJson, String algorithm, String parametersJson,
                     String metricsJson, String inputSnapshot, String artifactUri,
                     String manifestJson) {}

    interface JobExecutionContext {
        boolean cancellationRequested() throws Exception;
        void checkCancellation() throws Exception;
    }
}

package com.erplancers.erp.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Request contains logical dataset keys only; clients cannot submit SQL or physical table names. */
public record AnalyticsJobRequest(
        @NotBlank @Size(max = 160) String tenantId,
        @NotBlank @Size(max = 100) String datasetKey,
        @NotBlank @Size(max = 80) String jobType,
        @NotNull @Size(max = 32) Map<String, Object> parameters,
        @Size(max = 160) String idempotencyKey) {}

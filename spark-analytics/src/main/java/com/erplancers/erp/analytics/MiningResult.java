package com.erplancers.erp.analytics;

import java.util.List;
import java.util.Map;

/** Serializable-friendly summary returned by data-mining jobs. */
public record MiningResult(
        String algorithm,
        long inputRows,
        int outputGroups,
        List<Map<String, Object>> summaries,
        String modelPath,
        Map<String, Object> metrics) {
    public MiningResult {
        summaries = summaries == null ? List.of() : List.copyOf(summaries);
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }
}

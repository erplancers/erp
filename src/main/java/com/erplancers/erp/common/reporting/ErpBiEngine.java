package com.erplancers.erp.common.reporting;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Semantic ERP BI facade over the database aggregation and advanced analytics
 * engines.
 *
 * <p>BI consumers describe dimensions, measures and filters instead of writing
 * report-specific repository queries. The database performs the aggregation;
 * this service adds BI operations such as ranking, percentage-of-total and
 * grand totals.</p>
 */
@Service
@Transactional(readOnly = true)
public class ErpBiEngine {

    private final AggregateReportingService aggregates;
    private final AdvancedAnalyticsEngine advancedAnalytics;

    public ErpBiEngine(
            AggregateReportingService aggregates,
            AdvancedAnalyticsEngine advancedAnalytics) {
        this.aggregates = aggregates;
        this.advancedAnalytics = advancedAnalytics;
    }

    public BiReport query(String resource, BiQuery request) {
        AggregateReportingService.AggregateRequest aggregateRequest =
                new AggregateReportingService.AggregateRequest(
                        request.dimensions(),
                        request.measures(),
                        request.filters(),
                        request.orderBy(),
                        request.limit());

        AggregateReportingService.AggregateReport report =
                aggregates.aggregate(resource, aggregateRequest);

        String rankingMeasure = rankingMeasure(report.metrics());
        Double grandTotalValue = null;
        Map<String, Object> grandTotal = new LinkedHashMap<>();

        if (request.includeGrandTotal() || request.includeShareOfTotal()) {
            AggregateReportingService.AggregateReport totalReport =
                    aggregates.aggregate(
                            resource,
                            new AggregateReportingService.AggregateRequest(
                                    List.of(),
                                    List.of(rankingMeasure),
                                    request.filters(),
                                    "",
                                    1));

            if (!totalReport.data().isEmpty()) {
                Object value = totalReport.data().get(0).get(rankingMeasure);
                if (value instanceof Number number) {
                    grandTotalValue = number.doubleValue();
                }
            }

            if (request.includeGrandTotal() && !totalReport.data().isEmpty()) {
                grandTotal.putAll(totalReport.data().get(0));
            }
        }

        List<Map<String, Object>> rows = enrichRows(
                report.data(),
                rankingMeasure,
                grandTotalValue,
                request.includeRank(),
                request.includeShareOfTotal());

        return new BiReport(
                report.resource(),
                report.module(),
                report.groupBy(),
                report.metrics(),
                report.filters(),
                rows,
                grandTotal,
                rows.size());
    }

    public AdvancedAnalyticsEngine.AdvancedAnalysis analyze(
            String resource,
            BiAnalysisRequest request) {

        return advancedAnalytics.analyze(
                resource,
                new AdvancedAnalyticsEngine.AnalysisRequest(
                        request.fields(),
                        request.sampleSize(),
                        request.zScoreThreshold(),
                        request.aggregate()));
    }

    private List<Map<String, Object>> enrichRows(
            List<Map<String, Object>> source,
            String measure,
            Double grandTotal,
            boolean includeRank,
            boolean includeShareOfTotal) {

        List<Map<String, Object>> rows = source.stream()
                .map(LinkedHashMap::new)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        if (includeRank) {
            List<Map<String, Object>> ranked = rows.stream()
                    .sorted(Comparator.<Map<String, Object>>comparingDouble(
                            row -> numeric(row.get(measure))).reversed())
                    .toList();

            int rank = 1;
            for (Map<String, Object> row : ranked) {
                row.put("_rank", rank++);
            }
        }

        if (includeShareOfTotal && grandTotal != null && grandTotal != 0) {
            for (Map<String, Object> row : rows) {
                row.put("_shareOfTotal",
                        numeric(row.get(measure)) / grandTotal);
            }
        }

        return rows;
    }

    private String rankingMeasure(List<String> measures) {
        for (String measure : measures) {
            if (!"count".equalsIgnoreCase(measure)
                    && !"count(*)".equalsIgnoreCase(measure)) {
                return measure;
            }
        }
        return "count";
    }

    private double numeric(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    public record BiQuery(
            List<String> dimensions,
            List<String> measures,
            Map<String, String> filters,
            String orderBy,
            int limit,
            boolean includeRank,
            boolean includeShareOfTotal,
            boolean includeGrandTotal) {

        public BiQuery {
            dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
            measures = measures == null ? List.of("count") : List.copyOf(measures);
            filters = filters == null ? Map.of() : Map.copyOf(filters);
            orderBy = orderBy == null ? "" : orderBy;
            limit = limit <= 0 ? 100 : limit;
        }
    }

    public record BiReport(
            String resource,
            String module,
            List<String> dimensions,
            List<String> measures,
            Map<String, String> filters,
            List<Map<String, Object>> rows,
            Map<String, Object> grandTotal,
            int rowCount) {
    }

    public record BiAnalysisRequest(
            List<String> fields,
            int sampleSize,
            double zScoreThreshold,
            AggregateReportingService.AggregateRequest aggregate) {

        public BiAnalysisRequest {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }
}

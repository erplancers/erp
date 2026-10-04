package com.erplancers.erp.common.reporting;

import com.erplancers.erp.common.web.CrudRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Database-backed advanced analytics engine for registered ERP entities.
 *
 * <p>The engine intentionally works on scalar numeric attributes only. It fetches
 * a bounded sample from the database and calculates descriptive statistics,
 * quantiles, outlier counts and pairwise Pearson correlations without requiring
 * a separate analytics database.</p>
 */
@Service
@Transactional(readOnly = true)
public class AdvancedAnalyticsEngine {

    private static final int DEFAULT_SAMPLE_SIZE = 10_000;
    private static final int MAX_SAMPLE_SIZE = 50_000;
    private static final int MAX_FIELDS = 12;
    private static final double DEFAULT_Z_SCORE = 3.0;

    private final EntityManager entityManager;
    private final CrudRegistry registry;

    public AdvancedAnalyticsEngine(EntityManager entityManager, CrudRegistry registry) {
        this.entityManager = entityManager;
        this.registry = registry;
    }

    public AdvancedAnalysis analyze(String slug, AnalysisRequest request) {
        CrudRegistry.CrudDescriptor descriptor = registry.get(slug);
        EntityType<?> entity = entityManager.getMetamodel().entity(descriptor.entityType());

        int sampleSize = clamp(request.sampleSize(), 1, MAX_SAMPLE_SIZE, DEFAULT_SAMPLE_SIZE);
        double zScore = request.zScoreThreshold() > 0
                ? request.zScoreThreshold()
                : DEFAULT_Z_SCORE;

        List<String> fields = numericFields(entity, request.fields());
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("No numeric analytics fields available for " + slug);
        }
        if (fields.size() > MAX_FIELDS) {
            fields = fields.subList(0, MAX_FIELDS);
        }

        List<Object[]> rows = fetchRows(descriptor.entityType(), fields, sampleSize);

        Map<String, NumericAnalysis> statistics = new LinkedHashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            List<Double> values = column(rows, i);
            statistics.put(fields.get(i), statistics(values, zScore));
        }

        Map<String, Map<String, Double>> correlations = correlationMatrix(rows, fields);

        return new AdvancedAnalysis(
                slug,
                descriptor.module(),
                descriptor.entityType().getSimpleName(),
                fields,
                rows.size(),
                sampleSize,
                zScore,
                statistics,
                correlations
        );
    }

    private List<String> numericFields(EntityType<?> entity, List<String> requested) {
        List<String> available = entity.getAttributes().stream()
                .filter(this::isNumeric)
                .map(Attribute::getName)
                .sorted()
                .toList();

        if (requested == null || requested.isEmpty()) {
            return new ArrayList<>(available);
        }

        List<String> selected = new ArrayList<>();
        for (String field : requested) {
            if (!available.contains(field)) {
                throw new IllegalArgumentException(
                        "Unknown or non-numeric analytics field '" + field + "'");
            }
            if (!selected.contains(field)) {
                selected.add(field);
            }
        }
        return selected;
    }

    private boolean isNumeric(Attribute<?, ?> attribute) {
        if (attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.BASIC) {
            return false;
        }
        Class<?> type = attribute.getJavaType();
        return Number.class.isAssignableFrom(type)
                || type == byte.class
                || type == short.class
                || type == int.class
                || type == long.class
                || type == float.class
                || type == double.class;
    }

    private List<Object[]> fetchRows(Class<?> entityType, List<String> fields, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<?> root = query.from(entityType);

        List<Expression<?>> selections = fields.stream()
                .map(root::get)
                .map(expression -> (Expression<?>) expression)
                .toList();

        query.multiselect(selections.toArray(Expression[]::new));

        return entityManager.createQuery(query)
                .setMaxResults(limit)
                .getResultList();
    }

    private List<Double> column(List<Object[]> rows, int index) {
        List<Double> values = new ArrayList<>();
        for (Object[] row : rows) {
            if (row[index] instanceof Number number) {
                values.add(number.doubleValue());
            }
        }
        return values;
    }

    private NumericAnalysis statistics(List<Double> values, double zScore) {
        if (values.isEmpty()) {
            return new NumericAnalysis(0, null, null, null, null, null, null, null, 0);
        }

        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());

        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / values.size();

        double variance = values.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0.0);
        double standardDeviation = Math.sqrt(variance);

        long outliers = 0;
        if (standardDeviation > 0) {
            for (double value : values) {
                if (Math.abs((value - mean) / standardDeviation) >= zScore) {
                    outliers++;
                }
            }
        }

        return new NumericAnalysis(
                values.size(),
                mean,
                standardDeviation,
                sorted.get(0),
                sorted.get(sorted.size() - 1),
                quantile(sorted, 0.25),
                quantile(sorted, 0.50),
                quantile(sorted, 0.75),
                outliers
        );
    }

    private double quantile(List<Double> sorted, double probability) {
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        double position = probability * (sorted.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return sorted.get(lower);
        }
        double weight = position - lower;
        return sorted.get(lower) + weight * (sorted.get(upper) - sorted.get(lower));
    }

    private Map<String, Map<String, Double>> correlationMatrix(
            List<Object[]> rows, List<String> fields) {

        Map<String, Map<String, Double>> matrix = new LinkedHashMap<>();

        for (int i = 0; i < fields.size(); i++) {
            Map<String, Double> row = new LinkedHashMap<>();
            for (int j = 0; j < fields.size(); j++) {
                row.put(fields.get(j), pearson(rows, i, j));
            }
            matrix.put(fields.get(i), row);
        }

        return matrix;
    }

    private double pearson(List<Object[]> rows, int first, int second) {
        List<Double> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();

        for (Object[] row : rows) {
            if (row[first] instanceof Number a && row[second] instanceof Number b) {
                x.add(a.doubleValue());
                y.add(b.doubleValue());
            }
        }

        if (x.size() < 2) {
            return 0.0;
        }

        double meanX = x.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double meanY = y.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        double numerator = 0;
        double sumX = 0;
        double sumY = 0;

        for (int i = 0; i < x.size(); i++) {
            double dx = x.get(i) - meanX;
            double dy = y.get(i) - meanY;
            numerator += dx * dy;
            sumX += dx * dx;
            sumY += dy * dy;
        }

        if (sumX == 0 || sumY == 0) {
            return 0.0;
        }
        return numerator / Math.sqrt(sumX * sumY);
    }

    private int clamp(int value, int min, int max, int fallback) {
        return value <= 0 ? fallback : Math.min(value, max);
    }

    public record AnalysisRequest(
            List<String> fields,
            int sampleSize,
            double zScoreThreshold) {

        public AnalysisRequest {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    public record AdvancedAnalysis(
            String resource,
            String module,
            String entity,
            List<String> fields,
            int sampleSize,
            int requestedSampleSize,
            double zScoreThreshold,
            Map<String, NumericAnalysis> statistics,
            Map<String, Map<String, Double>> correlations) {}

    public record NumericAnalysis(
            long count,
            Double mean,
            Double standardDeviation,
            Double min,
            Double max,
            Double q1,
            Double median,
            Double q3,
            long outlierCount) {}
}

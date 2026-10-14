package com.erplancers.erp.analytics;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.RelationalGroupedDataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Safe Spark OLAP execution over server-configured logical dataset aliases. */
public final class SparkOlapMining {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Set<String> MODES = Set.of("GROUP", "ROLLUP", "CUBE");
    private static final int MAX_DIMENSIONS = 8;
    private static final int MAX_MEASURES = 12;
    private static final int MAX_FILTERS = 16;
    private static final int MAX_LIMIT = 1000;

    private SparkOlapMining() {}

    public static Dataset<Row> execute(
            Dataset<Row> source,
            List<String> dimensions,
            List<String> measures,
            Map<String, String> filters,
            String mode,
            String orderBy,
            int limit) {
        if (source == null) throw new IllegalArgumentException("Spark source is required");
        List<String> groups = dimensions == null ? List.of() : List.copyOf(dimensions);
        List<String> requestedMeasures = measures == null || measures.isEmpty()
                ? List.of("count") : List.copyOf(measures);
        Map<String, String> requestedFilters = filters == null ? Map.of() : new LinkedHashMap<>(filters);
        String resolvedMode = mode == null ? "GROUP" : mode.trim().toUpperCase(Locale.ROOT);

        if (!MODES.contains(resolvedMode)) throw new IllegalArgumentException("mode must be GROUP, ROLLUP, or CUBE");
        if (groups.size() > MAX_DIMENSIONS) throw new IllegalArgumentException("At most 8 OLAP dimensions are allowed");
        if (requestedMeasures.size() > MAX_MEASURES) throw new IllegalArgumentException("At most 12 OLAP measures are allowed");
        if (requestedFilters.size() > MAX_FILTERS) throw new IllegalArgumentException("At most 16 OLAP filters are allowed");
        if (limit < 1 || limit > MAX_LIMIT) throw new IllegalArgumentException("limit must be between 1 and 1000");
        if (!"GROUP".equals(resolvedMode) && groups.isEmpty()) {
            throw new IllegalArgumentException("ROLLUP and CUBE require at least one dimension");
        }

        Set<String> available = Set.copyOf(Arrays.asList(source.columns()));
        for (String dimension : groups) {
            requireIdentifier(dimension, "dimension");
            if (!available.contains(dimension)) throw new IllegalArgumentException("Unknown OLAP dimension: " + dimension);
        }
        for (String key : requestedFilters.keySet()) {
            requireIdentifier(key, "filter");
            if (!available.contains(key)) throw new IllegalArgumentException("Unknown OLAP filter column: " + key);
        }

        List<Column> aggregates = new ArrayList<>();
        Set<String> outputNames = new HashSet<>(groups);
        for (String measure : requestedMeasures) {
            Measure parsed = parseMeasure(measure, available);
            if (!outputNames.add(parsed.alias())) {
                throw new IllegalArgumentException("Duplicate OLAP output column: " + parsed.alias());
            }
            aggregates.add(parsed.expression().alias(parsed.alias()));
        }

        Dataset<Row> filtered = source;
        for (Map.Entry<String, String> filter : requestedFilters.entrySet()) {
            if (filter.getValue() == null) {
                filtered = filtered.filter(functions.col(filter.getKey()).isNull());
            } else {
                filtered = filtered.filter(functions.col(filter.getKey()).cast("string").equalTo(filter.getValue()));
            }
        }

        Column[] groupColumns = groups.stream().map(functions::col).toArray(Column[]::new);
        RelationalGroupedDataset grouped = switch (resolvedMode) {
            case "ROLLUP" -> filtered.rollup(groupColumns);
            case "CUBE" -> filtered.cube(groupColumns);
            default -> filtered.groupBy(groupColumns);
        };
        Dataset<Row> result;
        if (groups.isEmpty()) {
            result = filtered.agg(aggregates.getFirst(), aggregates.subList(1, aggregates.size()).toArray(Column[]::new));
        } else {
            result = grouped.agg(aggregates.getFirst(), aggregates.subList(1, aggregates.size()).toArray(Column[]::new));
        }

        if (orderBy != null && !orderBy.isBlank()) {
            String[] parts = orderBy.trim().split(":", -1);
            if (parts.length > 2 || !IDENTIFIER.matcher(parts[0]).matches()) {
                throw new IllegalArgumentException("orderBy must be outputColumn or outputColumn:asc|desc");
            }
            String target = parts[0];
            if (!Arrays.asList(result.columns()).contains(target)) {
                throw new IllegalArgumentException("orderBy must reference a selected output column: " + target);
            }
            String direction = parts.length == 1 ? "desc" : parts[1].toLowerCase(Locale.ROOT);
            if (!Set.of("asc", "desc").contains(direction)) {
                throw new IllegalArgumentException("orderBy direction must be asc or desc");
            }
            result = result.orderBy("asc".equals(direction)
                    ? functions.col(target).asc() : functions.col(target).desc());
        }
        return result.limit(limit);
    }

    private static Measure parseMeasure(String value, Set<String> available) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("OLAP measure cannot be blank");
        String normalized = value.trim();
        if ("count".equalsIgnoreCase(normalized) || "count(*)".equalsIgnoreCase(normalized)) {
            return new Measure("row_count", functions.count(functions.lit(1)));
        }
        String[] parts = normalized.split(":", -1);
        if (parts.length != 2 || !IDENTIFIER.matcher(parts[1]).matches()) {
            throw new IllegalArgumentException("Measure must be count or function:column");
        }
        String function = parts[0].trim().toLowerCase(Locale.ROOT);
        String field = parts[1].trim();
        if (!available.contains(field)) throw new IllegalArgumentException("Unknown OLAP measure column: " + field);
        Column column = functions.col(field);
        return switch (function) {
            case "sum" -> new Measure("sum_" + field, functions.sum(column));
            case "avg", "average" -> new Measure("avg_" + field, functions.avg(column));
            case "min" -> new Measure("min_" + field, functions.min(column));
            case "max" -> new Measure("max_" + field, functions.max(column));
            case "countdistinct", "count_distinct" -> new Measure("count_distinct_" + field, functions.countDistinct(column));
            case "count" -> new Measure("count_" + field, functions.count(column));
            default -> throw new IllegalArgumentException("Unsupported OLAP measure function: " + function);
        };
    }

    private static void requireIdentifier(String value, String kind) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid OLAP " + kind + " alias: " + value);
        }
    }

    private record Measure(String alias, Column expression) {}
}

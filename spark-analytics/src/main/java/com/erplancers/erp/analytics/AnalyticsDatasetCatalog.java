package com.erplancers.erp.analytics;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/** Loads an operator-managed source allowlist; no submitted job can change physical SQL sources. */
public final class AnalyticsDatasetCatalog {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,62}");
    private static final Pattern TABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,62}(\\.[A-Za-z_][A-Za-z0-9_]{0,62})?");
    private final Map<String, DatasetDefinition> definitions;

    public record Projection(String sourceColumn, String logicalAlias) {}
    public record DatasetDefinition(String key, String table, List<Projection> projections,
                                    String tenantColumn, String scopeColumn, String scopeType) {
        public DatasetDefinition { projections = List.copyOf(projections); }
        public Set<String> logicalColumns() {
            Set<String> names = new HashSet<>();
            projections.forEach(p -> names.add(p.logicalAlias()));
            return Set.copyOf(names);
        }
    }

    private AnalyticsDatasetCatalog(Map<String, DatasetDefinition> definitions) {
        this.definitions = Map.copyOf(definitions);
    }

    public static AnalyticsDatasetCatalog load(Path file) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
        Set<String> keys = new TreeSet<>();
        String prefix = "analytics.datasets.";
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(prefix) && name.endsWith(".table")) {
                keys.add(name.substring(prefix.length(), name.length() - ".table".length()));
            }
        }
        if (keys.isEmpty()) throw new IllegalArgumentException("No analytics dataset definitions configured");
        Map<String, DatasetDefinition> result = new HashMap<>();
        for (String key : keys) {
            String base = prefix + key + ".";
            String table = required(properties, base + "table");
            String sourceColumns = required(properties, base + "columns");
            String tenantColumn = required(properties, base + "tenant-column");
            String scopeColumn = required(properties, base + "scope-column");
            String scopeType = required(properties, base + "scope-type").toUpperCase(Locale.ROOT);
            if (!TABLE.matcher(table).matches()) throw new IllegalArgumentException("Unsafe table identifier for " + key);
            validateIdentifier(tenantColumn, "tenantColumn");
            validateIdentifier(scopeColumn, "scopeColumn");
            if (!Set.of("CONTACT", "WAREHOUSE", "COST_CENTER").contains(scopeType)) {
                throw new IllegalArgumentException("Unsupported scope type for " + key + ": " + scopeType);
            }
            List<Projection> projections = new ArrayList<>();
            Set<String> aliases = new HashSet<>();
            for (String item : sourceColumns.split(",")) {
                String[] pieces = item.trim().split(":", -1);
                if (pieces.length != 2) throw new IllegalArgumentException("Column mapping must be source:alias in " + key);
                validateIdentifier(pieces[0], "source column");
                validateIdentifier(pieces[1], "logical alias");
                if (!aliases.add(pieces[1])) throw new IllegalArgumentException("Duplicate logical alias: " + pieces[1]);
                projections.add(new Projection(pieces[0], pieces[1]));
            }
            if (projections.isEmpty()) throw new IllegalArgumentException("Dataset must select columns: " + key);
            Set<String> prohibitedAliases = Set.of("password", "secret", "access_token", "refresh_token", "private_key", "api_key");
            if (aliases.stream().map(a -> a.toLowerCase(Locale.ROOT)).anyMatch(prohibitedAliases::contains)) {
                throw new IllegalArgumentException("Credential-bearing columns are prohibited from analytics extracts");
            }
            result.put(key, new DatasetDefinition(key, table, projections, tenantColumn, scopeColumn, scopeType));
        }
        return new AnalyticsDatasetCatalog(result);
    }

    public DatasetDefinition require(String key) {
        DatasetDefinition definition = definitions.get(key);
        if (definition == null) throw new IllegalArgumentException("Dataset is not allowlisted: " + key);
        return definition;
    }

    public Set<String> keys() { return definitions.keySet(); }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank() || value.startsWith("YOUR_")) {
            throw new IllegalArgumentException("Missing/placeholder analytics setting: " + key);
        }
        return value.trim();
    }

    private static void validateIdentifier(String value, String name) {
        if (!IDENTIFIER.matcher(value).matches()) throw new IllegalArgumentException("Unsafe " + name + ": " + value);
    }
}

package com.erplancers.erp.analytics;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

/**
 * Reads only operator-allowlisted, explicitly projected views and enforces live tenant/scope
 * grants in PostgreSQL before rows are exposed to Spark transformations.
 */
public final class ScopedPostgresReader {
    private static final Pattern SAFE_TENANT = Pattern.compile("[A-Za-z0-9_.:@-]{1,160}");
    private final SparkSession spark;
    private final String jdbcUrl;
    private final String readOnlyUsername;
    private final String readOnlyPassword;

    public ScopedPostgresReader(SparkSession spark, String jdbcUrl, String readOnlyUsername, String readOnlyPassword) {
        this.spark = Objects.requireNonNull(spark);
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql:")) throw new IllegalArgumentException("PostgreSQL JDBC URL required");
        this.jdbcUrl = jdbcUrl;
        this.readOnlyUsername = requireText(readOnlyUsername, "read-only username");
        this.readOnlyPassword = requireText(readOnlyPassword, "read-only password");
    }

    public Dataset<Row> read(AnalyticsDatasetCatalog.DatasetDefinition definition,
                             AnalyticsJobStore.Job job) {
        Objects.requireNonNull(definition);
        Objects.requireNonNull(job);
        if (!definition.scopeType().equalsIgnoreCase(job.scopeType())) {
            throw new SecurityException("Job scope type does not match approved dataset catalog");
        }
        if (!SAFE_TENANT.matcher(job.tenantId()).matches()
                || job.requestedBy() == null || job.requestedBy().isBlank()) {
            throw new SecurityException("Invalid security context for analytics extraction");
        }

        String projections = definition.projections().stream()
                .map(p -> "src." + quoteIdentifier(p.sourceColumn()) + " AS " + quoteIdentifier(p.logicalAlias()))
                .collect(java.util.stream.Collectors.joining(", "));
        String table = java.util.Arrays.stream(definition.table().split("\\."))
                .map(ScopedPostgresReader::quoteIdentifier).collect(java.util.stream.Collectors.joining("."));
        String tenantColumn = "src." + quoteIdentifier(definition.tenantColumn());
        String scopeColumn = "src." + quoteIdentifier(definition.scopeColumn());
        String tenant = sqlLiteral(job.tenantId());
        String username = sqlLiteral(job.requestedBy());
        String scopeType = sqlLiteral(definition.scopeType().toUpperCase(Locale.ROOT));

        // Values originate from authenticated identity/queue records and are still escaped as SQL literals.
        // Identifiers are never supplied by the API and are validated by AnalyticsDatasetCatalog.
        String query = "SELECT " + projections + " FROM " + table + " src WHERE " + tenantColumn + " = " + tenant
                + " AND EXISTS (SELECT 1 FROM analytics_tenant_grant tg WHERE tg.username = " + username
                + " AND tg.tenant_id = " + tenant + " AND tg.active = TRUE)"
                + " AND EXISTS (SELECT 1 FROM analytics_scope_grant sg WHERE sg.username = " + username
                + " AND sg.tenant_id = " + tenant + " AND sg.scope_type = " + scopeType
                + " AND sg.active = TRUE AND sg.scope_id = CAST(" + scopeColumn + " AS VARCHAR))";

        return spark.read().format("jdbc")
                .option("url", jdbcUrl)
                .option("dbtable", "(" + query + ") AS scoped_analytics_source")
                .option("user", readOnlyUsername)
                .option("password", readOnlyPassword)
                .option("driver", "org.postgresql.Driver")
                .option("fetchsize", "10000")
                .option("queryTimeout", "600")
                .load();
    }

    private static String quoteIdentifier(String value) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("Unsafe SQL identifier");
        }
        return "\"" + value + "\"";
    }

    private static String sqlLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}

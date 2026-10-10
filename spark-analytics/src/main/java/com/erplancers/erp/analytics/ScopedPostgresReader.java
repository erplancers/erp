package com.erplancers.erp.analytics;

import java.util.Locale;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
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
                .option("sessionInitStatement", "SET default_transaction_read_only = on")
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

    /** Fingerprints the exact active row-scope grant set used to create a model baseline. */
    public String scopeFingerprint(AnalyticsDatasetCatalog.DatasetDefinition definition,
                                   AnalyticsJobStore.Job job) throws SQLException {
        try (Connection connection = openReadOnlyConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT sg.scope_id
                       FROM analytics_scope_grant sg
                       JOIN analytics_tenant_grant tg
                         ON tg.username = sg.username AND tg.tenant_id = sg.tenant_id
                      WHERE sg.username = ? AND sg.tenant_id = ? AND sg.scope_type = ?
                        AND sg.active = TRUE AND tg.active = TRUE
                      ORDER BY sg.scope_id
                     """)) {
            statement.setString(1, job.requestedBy());
            statement.setString(2, job.tenantId());
            statement.setString(3, definition.scopeType());
            StringBuilder canonical = new StringBuilder();
            int count = 0;
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    canonical.append(rows.getString(1)).append('\\n');
                    count++;
                }
            }
            if (count == 0) throw new SecurityException("No active scope grants exist at execution time");
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
                return "sha256:" + java.util.HexFormat.of().formatHex(digest);
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 is unavailable", impossible);
            }
        }
    }

    private Connection openReadOnlyConnection() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", readOnlyUsername);
        properties.setProperty("password", readOnlyPassword);
        properties.setProperty("readOnly", "true");
        properties.setProperty("ApplicationName", "erplancers-spark-scope-check");
        Connection connection = DriverManager.getConnection(jdbcUrl, properties);
        connection.setReadOnly(true);
        return connection;
    }

}

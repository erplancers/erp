package com.erplancers.erp.analytics;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Reads analytics inputs through the ERP's configured JPA EntityManager and entity mappings.
 * No secondary JDBC connection, SQL table names, or direct Spark JDBC extraction are used.
 *
 * The configured scope attribute may be either a scalar foreign key or a JPA to-one
 * relationship; relationship values are filtered by their identifier.
 */
@Component
public class JpaAnalyticsDataReader implements AnalyticsSourceReader {
    private static final int PAGE_SIZE = 2_000;
    private static final int MAX_ROWS = 1_000_000;

    private final EntityManager entityManager;
    private final SparkSessionProvider sparkProvider;

    public JpaAnalyticsDataReader(EntityManager entityManager, SparkSessionProvider sparkProvider) {
        this.entityManager = Objects.requireNonNull(entityManager);
        this.sparkProvider = Objects.requireNonNull(sparkProvider);
    }

    @Override
    @Transactional(readOnly = true)
    public Dataset<Row> read(AnalyticsDatasetCatalog.DatasetDefinition definition,
                             AnalyticsJobStore.Job job) {
        verifyTenantAndScope(definition, job);
        EntityType<?> entityType = entityType(definition.table());
        Class<?> entityClass = entityType.getJavaType();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<?> root = query.from(entityClass);

        List<Path<?>> selected = new ArrayList<>();
        List<StructField> fields = new ArrayList<>();
        for (AnalyticsDatasetCatalog.Projection projection : definition.projections()) {
            Path<?> path = attributePath(root, projection.sourceColumn());
            selected.add(path);
            fields.add(DataTypes.createStructField(projection.logicalAlias(),
                    sparkType(path.getJavaType()), true));
        }
        query.multiselect(selected);

        List<String> grantedScopes = activeScopes(definition, job);
        if (grantedScopes.isEmpty()) {
            throw new SecurityException("No active row-scope grants exist for analytics extraction");
        }
        Path<?> scopePath = scopePath(root, entityType, definition.scopeColumn());
        Class<?> scopeType = scopePath.getJavaType();
        List<Object> typedScopes = grantedScopes.stream().map(value -> convert(value, scopeType)).toList();
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(scopePath.in(typedScopes));

        if (!"none".equalsIgnoreCase(definition.tenantColumn())) {
            Path<?> tenantPath = attributePath(root, definition.tenantColumn());
            predicates.add(cb.equal(tenantPath, convert(job.tenantId(), tenantPath.getJavaType())));
        }
        query.where(cb.and(predicates.toArray(Predicate[]::new)));

        StructType schema = DataTypes.createStructType(fields);
        SparkSession spark = SparkSession.builder().getOrCreate();
        List<Row> rows = new ArrayList<>();
        int offset = 0;
        while (true) {
            TypedQuery<Object[]> page = entityManager.createQuery(query)
                    .setFirstResult(offset)
                    .setMaxResults(PAGE_SIZE);
            List<Object[]> batch = page.getResultList();
            if (batch.isEmpty()) break;
            for (Object[] values : batch) {
                Object[] normalized = new Object[values.length];
                for (int i = 0; i < values.length; i++) normalized[i] = normalize(values[i]);
                rows.add(RowFactory.create(normalized));
                if (rows.size() > MAX_ROWS) {
                    throw new IllegalArgumentException("JPA analytics extraction exceeds " + MAX_ROWS
                            + " rows; use an approved aggregate/projection entity or a bounded time window");
                }
            }
            offset += batch.size();
            if (batch.size() < PAGE_SIZE) break;
        }
        return spark.createDataFrame(rows, schema);
    }

    @Override
    @Transactional(readOnly = true)
    public String scopeFingerprint(AnalyticsDatasetCatalog.DatasetDefinition definition,
                                   AnalyticsJobStore.Job job) {
        List<String> scopes = activeScopes(definition, job);
        if (scopes.isEmpty()) throw new SecurityException("No active scope grants exist at execution time");
        scopes = scopes.stream().sorted().toList();
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", scopes).concat("\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "sha256:" + java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void verifyTenantAndScope(AnalyticsDatasetCatalog.DatasetDefinition definition,
                                      AnalyticsJobStore.Job job) {
        if (!definition.scopeType().equalsIgnoreCase(job.scopeType())) {
            throw new SecurityException("Job scope type does not match approved JPA dataset");
        }
        Number tenantGrant = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM analytics_tenant_grant
                 WHERE username = :username AND tenant_id = :tenant AND active = TRUE
                """).setParameter("username", job.requestedBy())
                .setParameter("tenant", job.tenantId()).getSingleResult();
        if (tenantGrant.longValue() < 1) throw new SecurityException("No active tenant grant");
    }

    @SuppressWarnings("unchecked")
    private List<String> activeScopes(AnalyticsDatasetCatalog.DatasetDefinition definition,
                                      AnalyticsJobStore.Job job) {
        return entityManager.createNativeQuery("""
                SELECT sg.scope_id
                  FROM analytics_scope_grant sg
                  JOIN analytics_tenant_grant tg
                    ON tg.username = sg.username AND tg.tenant_id = sg.tenant_id
                 WHERE sg.username = :username AND sg.tenant_id = :tenant
                   AND sg.scope_type = :scopeType
                   AND sg.active = TRUE AND tg.active = TRUE
                 ORDER BY sg.scope_id
                """).setParameter("username", job.requestedBy())
                .setParameter("tenant", job.tenantId())
                .setParameter("scopeType", definition.scopeType().toUpperCase(Locale.ROOT))
                .getResultList().stream().map(Object::toString).toList();
    }

    private EntityType<?> entityType(String configuredName) {
        return entityManager.getMetamodel().getEntities().stream()
                .filter(type -> type.getName().equals(configuredName)
                        || type.getJavaType().getSimpleName().equals(configuredName)
                        || type.getJavaType().getName().equals(configuredName))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Analytics source must name a registered JPA entity, not a table: " + configuredName));
    }

    private Path<?> scopePath(Root<?> root, EntityType<?> entityType, String name) {
        Path<?> path = attributePath(root, name);
        if (path.getModel() instanceof SingularAttribute<?, ?> attribute
                && attribute.getPersistentAttributeType().name().matches("MANY_TO_ONE|ONE_TO_ONE")) {
            EntityType<?> related = entityManager.getMetamodel().entity(path.getJavaType());
            String idName = related.getSingularAttributes().stream()\n                    .filter(SingularAttribute::isId)\n                    .map(SingularAttribute::getName)\n                    .findFirst().orElseThrow(() -> new IllegalArgumentException(\n                            "Scope relationship has no simple JPA identifier: " + name));
            return path.get(idName);
        }
        return path;
    }

    private Path<?> attributePath(Path<?> root, String name) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("Analytics projections must use approved JPA scalar attributes: " + name);
        }
        try {
            return root.get(name);
        } catch (IllegalArgumentException invalidAttribute) {
            throw new IllegalArgumentException("Unknown JPA attribute '" + name + "' on analytics entity", invalidAttribute);
        }
    }

    private Object convert(String value, Class<?> type) {
        if (type == String.class || type == Object.class) return value;
        if (type == java.util.UUID.class) return java.util.UUID.fromString(value);
        if (type == Long.class || type == long.class) return Long.valueOf(value);
        if (type == Integer.class || type == int.class) return Integer.valueOf(value);
        if (type == Short.class || type == short.class) return Short.valueOf(value);
        if (type == java.math.BigInteger.class) return new java.math.BigInteger(value);
        if (type == BigDecimal.class) return new BigDecimal(value);
        if (type.isEnum()) return Enum.valueOf((Class) type, value);
        throw new IllegalArgumentException("Unsupported JPA scope identifier type: " + type.getName());
    }

    private DataType sparkType(Class<?> type) {
        if (type == String.class || type.isEnum() || type == java.util.UUID.class) return DataTypes.StringType;
        if (type == Integer.class || type == int.class || type == Short.class || type == short.class) return DataTypes.IntegerType;
        if (type == Long.class || type == long.class || type == java.math.BigInteger.class) return DataTypes.LongType;
        if (type == Float.class || type == float.class) return DataTypes.FloatType;
        if (type == Double.class || type == double.class) return DataTypes.DoubleType;
        if (Number.class.isAssignableFrom(type) || type == BigDecimal.class) return DataTypes.DoubleType;
        if (type == Boolean.class || type == boolean.class) return DataTypes.BooleanType;
        if (type == LocalDate.class || type == java.sql.Date.class) return DataTypes.DateType;
        if (type == LocalDateTime.class || type == Instant.class || type == Timestamp.class
                || java.util.Date.class.isAssignableFrom(type)) return DataTypes.TimestampType;
        throw new IllegalArgumentException("Unsupported JPA analytics projection type: " + type.getName());
    }

    private Object normalize(Object value) {
        if (value == null) return null;
        if (value instanceof java.util.UUID || value.getClass().isEnum()) return value.toString();
        if (value instanceof BigDecimal decimal) return decimal.doubleValue();
        if (value instanceof java.math.BigInteger integer) return integer.longValue();
        if (value instanceof LocalDate date) return java.sql.Date.valueOf(date);
        if (value instanceof LocalDateTime dateTime) return Timestamp.valueOf(dateTime);
        if (value instanceof Instant instant) return Timestamp.from(instant);
        if (value instanceof java.util.Date date && !(value instanceof java.sql.Date)
                && !(value instanceof Timestamp)) return new Timestamp(date.getTime());
        return value;
    }
}

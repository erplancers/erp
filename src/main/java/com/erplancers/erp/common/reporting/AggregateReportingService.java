package com.erplancers.erp.common.reporting;

import com.erplancers.erp.common.web.CrudRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AggregateReportingService {

    private final EntityManager entityManager;
    private final CrudRegistry registry;

    public AggregateReportingService(EntityManager entityManager, CrudRegistry registry) {
        this.entityManager = entityManager;
        this.registry = registry;
    }

    public AggregateReport aggregate(String slug, AggregateRequest request) {
        CrudRegistry.CrudDescriptor descriptor = descriptor(slug);
        validate(request, descriptor.entityType());

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<?> root = query.from(descriptor.entityType());

        List<Expression<?>> selections = new ArrayList<>();
        List<Expression<?>> groups = new ArrayList<>();

        for (String field : request.groupBy()) {
            Expression<?> expression = root.get(field);
            selections.add(expression);
            groups.add(expression);
        }

        List<String> metrics = request.metrics().isEmpty()
                ? List.of("count")
                : request.metrics();

        for (String metric : metrics) {
            selections.add(metricExpression(cb, root, metric));
        }

        query.multiselect(selections);
        Predicate predicate = predicates(cb, root, request.filters());
        if (predicate != null) {
            query.where(predicate);
        }
        if (!groups.isEmpty()) {
            query.groupBy(groups);
        }

        if (!request.orderBy().isBlank() && request.orderBy().contains(":")) {
            String[] order = request.orderBy().split(":", 2);
            Expression<?> orderExpression = order[0].equalsIgnoreCase("count")
                    ? metricExpression(cb, root, "count")
                    : root.get(order[0]);
            query.orderBy("desc".equalsIgnoreCase(order[1])
                    ? cb.desc(orderExpression)
                    : cb.asc(orderExpression));
        }

        int limit = Math.max(1, Math.min(request.limit(), 1000));
        List<Object[]> rows = entityManager.createQuery(query)
                .setMaxResults(limit)
                .getResultList();

        List<Map<String, Object>> data = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> values = new LinkedHashMap<>();
            int index = 0;
            for (String field : request.groupBy()) {
                values.put(field, row[index++]);
            }
            for (String metric : metrics) {
                values.put(metric, row[index++]);
            }
            data.add(values);
        }

        return new AggregateReport(slug, descriptor.module(), request.groupBy(), metrics,
                request.filters(), data);
    }

    private Expression<?> metricExpression(CriteriaBuilder cb, Root<?> root, String metric) {
        String normalized = metric.trim().toLowerCase();
        if ("count".equals(normalized) || "count(*)".equals(normalized)) {
            return cb.count(root);
        }

        int colon = normalized.indexOf(':');
        if (colon <= 0 || colon == normalized.length() - 1) {
            throw new IllegalArgumentException(
                    "Metric must be count or function:field (sum, avg, min, max)");
        }

        String function = normalized.substring(0, colon);
        String field = metric.substring(colon + 1);
        PropertyDescriptor property = property(root.getJavaType(), field);
        Class<?> type = property.getPropertyType();

        if (!isNumber(type)) {
            throw new IllegalArgumentException("Metric " + metric + " requires a numeric field");
        }

        Expression<? extends Number> expression = root.get(field);
        return switch (function) {
            case "sum" -> cb.sum(expression);
            case "avg" -> cb.avg(expression);
            case "min" -> cb.min(expression);
            case "max" -> cb.max(expression);
            default -> throw new IllegalArgumentException("Unsupported metric: " + function);
        };
    }

    private Predicate predicates(CriteriaBuilder cb, Root<?> root, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) return null;
        List<Predicate> predicates = new ArrayList<>();

        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            String operator = "eq";
            String field = key;

            int colon = key.lastIndexOf(':');
            if (colon > 0) {
                field = key.substring(0, colon);
                operator = key.substring(colon + 1).toLowerCase();
            }

            PropertyDescriptor property = property(root.getJavaType(), field);
            Class<?> type = property.getPropertyType();
            Expression<?> expression = root.get(field);
            Object typed = convert(value, type);

            predicates.add(switch (operator) {
                case "eq" -> cb.equal(expression, typed);
                case "ne" -> cb.notEqual(expression, typed);
                case "like" -> cb.like(cb.lower(expression.as(String.class)), value.toLowerCase());
                case "gt" -> cb.greaterThan(asComparable(expression), (Comparable) typed);
                case "gte" -> cb.greaterThanOrEqualTo(asComparable(expression), (Comparable) typed);
                case "lt" -> cb.lessThan(asComparable(expression), (Comparable) typed);
                case "lte" -> cb.lessThanOrEqualTo(asComparable(expression), (Comparable) typed);
                case "isnull" -> cb.isNull(expression);
                case "notnull" -> cb.isNotNull(expression);
                default -> throw new IllegalArgumentException("Unsupported filter operator: " + operator);
            });
        }

        return cb.and(predicates.toArray(Predicate[]::new));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Expression<? extends Comparable> asComparable(Expression<?> expression) {
        return (Expression) expression;
    }

    private Object convert(String value, Class<?> type) {
        if (value == null || "null".equalsIgnoreCase(value)) return null;
        if (type == String.class) return value;
        if (type == Integer.class || type == int.class) return Integer.valueOf(value);
        if (type == Long.class || type == long.class) return Long.valueOf(value);
        if (type == Double.class || type == double.class) return Double.valueOf(value);
        if (type == Float.class || type == float.class) return Float.valueOf(value);
        if (type == Short.class || type == short.class) return Short.valueOf(value);
        if (type == BigDecimal.class) return new BigDecimal(value);
        if (type == Boolean.class || type == boolean.class) return Boolean.valueOf(value);
        if (type == LocalDate.class) return LocalDate.parse(value);
        if (type == LocalDateTime.class) return LocalDateTime.parse(value);
        if (type == Instant.class) return Instant.parse(value);
        if (type == OffsetDateTime.class) return OffsetDateTime.parse(value);
        if (type == ZonedDateTime.class) return ZonedDateTime.parse(value);
        if (type == OffsetTime.class) return OffsetTime.parse(value);
        if (type.isEnum()) {
            return Arrays.stream(type.getEnumConstants())
                    .filter(v -> v.toString().equalsIgnoreCase(value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid " + type.getSimpleName() + ": " + value));
        }
        throw new IllegalArgumentException("Unsupported filter type: " + type.getName());
    }

    private void validate(AggregateRequest request, Class<?> type) {
        for (String field : request.groupBy()) property(type, field);
        for (String metric : request.metrics()) {
            if (!metric.equalsIgnoreCase("count")) {
                int colon = metric.indexOf(':');
                if (colon <= 0) throw new IllegalArgumentException("Invalid metric: " + metric);
                property(type, metric.substring(colon + 1));
            }
        }
        if (request.filters() != null) {
            for (String key : request.filters().keySet()) {
                String field = key;
                int colon = key.lastIndexOf(':');
                if (colon > 0) field = key.substring(0, colon);
                property(type, field);
            }
        }
    }

    private CrudRegistry.CrudDescriptor descriptor(String slug) {
        CrudRegistry.CrudDescriptor descriptor = registry.all().get(slug);
        if (descriptor == null) throw new IllegalArgumentException("Unknown ERP resource: " + slug);
        return descriptor;
    }

    private PropertyDescriptor property(Class<?> type, String name) {
        try {
            for (PropertyDescriptor p : Introspector.getBeanInfo(type).getPropertyDescriptors()) {
                if (p.getName().equals(name) && p.getReadMethod() != null
                        && !"class".equals(name) && !"id".equals(name) && !"version".equals(name)) {
                    return p;
                }
            }
        } catch (Exception ignored) {
        }
        throw new IllegalArgumentException("Unknown report field '" + name + "' on " + type.getSimpleName());
    }

    private boolean isNumber(Class<?> type) {
        return type != null && (Number.class.isAssignableFrom(type) || type == byte.class
                || type == short.class || type == int.class || type == long.class
                || type == float.class || type == double.class);
    }

    public record AggregateRequest(
            List<String> groupBy,
            List<String> metrics,
            Map<String, String> filters,
            String orderBy,
            int limit) {

        public AggregateRequest {
            groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
            metrics = metrics == null ? List.of("count") : List.copyOf(metrics);
            filters = filters == null ? Map.of() : Map.copyOf(filters);
            orderBy = orderBy == null ? "" : orderBy;
            limit = limit <= 0 ? 100 : limit;
        }
    }

    public record AggregateReport(
            String resource,
            String module,
            List<String> groupBy,
            List<String> metrics,
            Map<String, String> filters,
            List<Map<String, Object>> data) {
    }
}

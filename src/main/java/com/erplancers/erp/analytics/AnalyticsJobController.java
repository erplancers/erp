package com.erplancers.erp.analytics;

import com.erplancers.erp.common.security.PolicyAuthorizationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Secured analytics endpoints. Jobs are queued over HTTP and executed by the managed embedded engine, never on request threads.
 * Tenant/scope grants are managed out-of-band by an administrator.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analytics")
@SecurityRequirement(name = "basicAuth")
public class AnalyticsJobController {
    private static final Pattern SAFE_ALIAS = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Set<String> SUPPORTED_JOBS = Set.of(
            "SALES_TREND_ANALYSIS", "RFM", "COHORT_ANALYSIS", "CUSTOMER_LIFETIME_VALUE", "CUSTOMER_SEGMENTATION", "CUSTOMER_CHURN_RISK",
            "DEMAND_FORECAST", "STOCKOUT_RISK", "INVENTORY_ANOMALY",
            "MARKET_BASKET", "PRODUCT_RECOMMENDATIONS", "RECEIVABLES_RISK", "DATA_DRIFT", "MODEL_SCORING", "SPARK_OLAP");
    private static final Map<String, DatasetPolicy> DATASETS = Map.of(
            "sales_orders", new DatasetPolicy("CONTACT", Set.of("SALES_TREND_ANALYSIS", "RFM", "COHORT_ANALYSIS",
                    "CUSTOMER_LIFETIME_VALUE", "CUSTOMER_SEGMENTATION", "CUSTOMER_CHURN_RISK", "DATA_DRIFT", "MODEL_SCORING", "SPARK_OLAP")),
            "inventory_daily", new DatasetPolicy("WAREHOUSE", Set.of("DEMAND_FORECAST", "STOCKOUT_RISK", "INVENTORY_ANOMALY", "DATA_DRIFT", "MODEL_SCORING", "SPARK_OLAP")),
            "order_lines", new DatasetPolicy("CONTACT", Set.of("MARKET_BASKET", "PRODUCT_RECOMMENDATIONS", "SPARK_OLAP")),
            "receivables", new DatasetPolicy("CONTACT", Set.of("RECEIVABLES_RISK", "DATA_DRIFT", "MODEL_SCORING", "SPARK_OLAP")));
    private static final Map<String, Set<String>> PARAMETER_ALLOWLIST = Map.ofEntries(
            Map.entry("SALES_TREND_ANALYSIS", Set.of()),
            Map.entry("RFM", Set.of("asOfDate")),
            Map.entry("COHORT_ANALYSIS", Set.of()),
            Map.entry("CUSTOMER_LIFETIME_VALUE", Set.of()),
            Map.entry("CUSTOMER_SEGMENTATION", Set.of("featureColumns", "clusters", "maxIterations")),
            Map.entry("CUSTOMER_CHURN_RISK", Set.of("featureColumns", "labelColumn", "trainThroughDate", "algorithm", "reviewThreshold")),
            Map.entry("DEMAND_FORECAST", Set.of("trainThroughDate")),
            Map.entry("STOCKOUT_RISK", Set.of("featureColumns", "labelColumn", "trainThroughDate")),
            Map.entry("INVENTORY_ANOMALY", Set.of("zThreshold")),
            Map.entry("MARKET_BASKET", Set.of("minSupport", "minConfidence")),
            Map.entry("PRODUCT_RECOMMENDATIONS", Set.of("topN", "rank", "maxIterations", "regularization")),
            Map.entry("RECEIVABLES_RISK", Set.of("featureColumns", "labelColumn", "reviewThreshold", "trainThroughDate")),
            Map.entry("DATA_DRIFT", Set.of("modelName", "modelVersion", "featureColumns", "alertThreshold", "asOfDate")),
            Map.entry("MODEL_SCORING", Set.of("modelName", "modelVersion", "asOfDate", "reviewThreshold")),\n            Map.entry("SPARK_OLAP", Set.of("groupBy", "measures", "filters", "mode", "orderBy", "limit")));

    private final JdbcTemplate jdbc;
    private final PolicyAuthorizationService authorization;
    private final ObjectMapper objectMapper;

    @PostMapping("/jobs")
    @Transactional
    public ResponseEntity<JobView> submit(Authentication authentication, @Valid @RequestBody AnalyticsJobRequest request) {
        require(authentication, "WRITE");
        String jobType = request.jobType().trim().toUpperCase(Locale.ROOT);
        DatasetPolicy dataset = DATASETS.get(request.datasetKey());
        if (!SUPPORTED_JOBS.contains(jobType) || dataset == null || !dataset.jobTypes().contains(jobType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported job type/dataset combination");
        }
        validateParameters(jobType, request.parameters());
        requireTenantGrant(authentication.getName(), request.tenantId());
        requireScopeGrant(authentication.getName(), request.tenantId(), dataset.scopeType());

        String parametersJson;
        try {
            parametersJson = objectMapper.writeValueAsString(request.parameters());
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid job parameters");
        }
        String id = UUID.randomUUID().toString();
        int inserted = jdbc.update("""
                INSERT INTO analytics_job
                (id, tenant_id, requested_by, job_type, dataset_key, scope_type,
                 parameters_json, idempotency_key, status, attempt_count, max_attempts)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', 0, 3)
                ON CONFLICT (tenant_id, requested_by, idempotency_key) DO NOTHING
                """, id, request.tenantId(), authentication.getName(), jobType,
                request.datasetKey(), dataset.scopeType(), parametersJson, request.idempotencyKey());
        if (inserted == 0) {
            List<JobView> existing = findJob(request.tenantId(), authentication.getName(), request.idempotencyKey());
            if (existing.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already exists");
            return ResponseEntity.accepted().body(existing.getFirst());
        }
        String eventId = UUID.randomUUID().toString();
        String eventPayload;
        try {
            eventPayload = objectMapper.writeValueAsString(Map.of(
                    "eventId", eventId,
                    "eventType", "analytics.job.queued",
                    "schemaVersion", 1,
                    "occurredAt", Instant.now().toString(),
                    "jobId", id,
                    "tenantId", request.tenantId(),
                    "jobType", jobType,
                    "datasetKey", request.datasetKey()));
        } catch (JsonProcessingException serializationFailure) {
            throw new IllegalStateException("Could not serialize analytics outbox event", serializationFailure);
        }
        jdbc.update("""
                INSERT INTO analytics_event_outbox (event_id, aggregate_id, topic, event_key, payload_json)
                VALUES (?, ?, 'erp.analytics.job.queued.v1', ?, ?)
                """, eventId, id, id, eventPayload);
        return ResponseEntity.accepted().body(getOwnedJob(id, request.tenantId(), authentication.getName()));
    }

    @GetMapping("/jobs")
    public List<JobView> list(Authentication authentication, @RequestParam String tenantId,
                              @RequestParam(required = false) String status) {
        require(authentication, "READ");
        requireTenantGrant(authentication.getName(), tenantId);
        if (status != null && !Set.of("QUEUED", "RUNNING", "CANCEL_REQUESTED", "SUCCEEDED", "FAILED", "CANCELLED")
                .contains(status.toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown job status");
        }
        String query = """
                SELECT id, tenant_id, job_type, dataset_key, status, attempt_count,
                       max_attempts, result_json, error_message, created_at, updated_at
                FROM analytics_job WHERE tenant_id = ? AND requested_by = ?
                """ + (status == null ? "" : " AND status = ?") + " ORDER BY created_at DESC LIMIT 100";
        return status == null
                ? jdbc.query(query, (rs, row) -> mapJob(rs), tenantId, authentication.getName())
                : jdbc.query(query, (rs, row) -> mapJob(rs), tenantId, authentication.getName(), status.toUpperCase(Locale.ROOT));
    }

    @GetMapping("/jobs/{id}")
    public JobView get(Authentication authentication, @PathVariable String id, @RequestParam String tenantId) {
        require(authentication, "READ");
        requireTenantGrant(authentication.getName(), tenantId);
        return getOwnedJob(id, tenantId, authentication.getName());
    }

    @PostMapping("/jobs/{id}/cancel")
    public JobView cancel(Authentication authentication, @PathVariable String id, @RequestParam String tenantId) {
        require(authentication, "WRITE");
        requireTenantGrant(authentication.getName(), tenantId);
        int changed = jdbc.update("""
                UPDATE analytics_job
                SET status = CASE WHEN status = 'QUEUED' THEN 'CANCELLED' ELSE 'CANCEL_REQUESTED' END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND tenant_id = ? AND requested_by = ?
                  AND status IN ('QUEUED', 'RUNNING')
                """, id, tenantId, authentication.getName());
        if (changed == 0) {
            JobView existing = getOwnedJob(id, tenantId, authentication.getName());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Job is no longer cancellable; current state is " + existing.status());
        }
        return getOwnedJob(id, tenantId, authentication.getName());
    }

    private void validateParameters(String jobType, Map<String, Object> parameters) {
        if (parameters == null || parameters.size() > 32) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid parameters");
        Set<String> allowed = PARAMETER_ALLOWLIST.get(jobType);
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported parameter: " + entry.getKey());
            }
            if ("featureColumns".equals(entry.getKey())) {
                if (!(entry.getValue() instanceof List<?> features) || features.isEmpty() || features.size() > 64
                        || features.stream().anyMatch(x -> !(x instanceof String s) || !SAFE_ALIAS.matcher(s).matches())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "featureColumns must be 1-64 safe logical column aliases");
                }
            }
            if ("labelColumn".equals(entry.getKey()) && (!(entry.getValue() instanceof String label) || !SAFE_ALIAS.matcher(label).matches())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "labelColumn must be a safe logical column alias");
            }
            if ("algorithm".equals(entry.getKey()) && (!(entry.getValue() instanceof String algorithm)
                    || !Set.of("LOGISTIC_REGRESSION", "RANDOM_FOREST", "GRADIENT_BOOSTED_TREES").contains(algorithm))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported churn model algorithm");
            }
            if (Set.of("topN", "rank", "maxIterations").contains(entry.getKey())) {
                if (!(entry.getValue() instanceof Number number) || number.doubleValue() != number.intValue()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, entry.getKey() + " must be an integer");
                }
                int value = number.intValue();
                boolean valid = switch (entry.getKey()) {
                    case "topN" -> value >= 1 && value <= 100;
                    case "rank" -> value >= 2 && value <= 128;
                    case "maxIterations" -> value >= 1 && value <= 100;
                    default -> false;
                };
                if (!valid) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Out-of-range " + entry.getKey());
            }
            if ("regularization".equals(entry.getKey())) {
                if (!(entry.getValue() instanceof Number number)
                        || !Double.isFinite(number.doubleValue())
                        || number.doubleValue() < 0.0 || number.doubleValue() > 10.0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "regularization must be a finite number in [0,10]");
                }
            }
            if ("groupBy".equals(entry.getKey()) || "measures".equals(entry.getKey())) {
                int maximum = "groupBy".equals(entry.getKey()) ? 8 : 12;
                if (!(entry.getValue() instanceof List<?> columns)
                        || columns.size() > maximum
                        || ("measures".equals(entry.getKey()) && columns.isEmpty())
                        || columns.stream().anyMatch(item -> !(item instanceof String))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, entry.getKey() + " must be a bounded string list");
                }
                for (Object item : columns) {
                    String value = ((String) item).trim();
                    if ("groupBy".equals(entry.getKey())) {
                        if (!SAFE_ALIAS.matcher(value).matches()) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsafe OLAP dimension alias");
                        }
                    } else if (!value.matches("(?i)count(\\(\\*\\))?|(?:sum|avg|average|min|max|countdistinct|count_distinct|count):[A-Za-z_][A-Za-z0-9_]{0,63}")) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported OLAP measure: " + value);
                    }
                }
            }
            if ("filters".equals(entry.getKey())) {
                if (!(entry.getValue() instanceof Map<?, ?> filters) || filters.size() > 16) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filters must be a map with at most 16 entries");
                }
                for (Map.Entry<?, ?> filter : filters.entrySet()) {
                    if (!(filter.getKey() instanceof String key) || !SAFE_ALIAS.matcher(key).matches()
                            || (filter.getValue() != null && (!(filter.getValue() instanceof String value) || value.length() > 500))) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OLAP filter entry");
                    }
                }
            }
            if ("mode".equals(entry.getKey()) && (!(entry.getValue() instanceof String mode)
                    || !Set.of("GROUP", "ROLLUP", "CUBE").contains(mode.toUpperCase(Locale.ROOT)))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode must be GROUP, ROLLUP, or CUBE");
            }
            if ("orderBy".equals(entry.getKey()) && (!(entry.getValue() instanceof String order)
                    || (!order.isBlank() && !order.matches("[A-Za-z_][A-Za-z0-9_]{0,63}(:asc|:desc)?")))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OLAP orderBy expression");
            }
            if ("limit".equals(entry.getKey())) {
                if (!(entry.getValue() instanceof Number number) || number.doubleValue() != number.intValue()
                        || number.intValue() < 1 || number.intValue() > 1000) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OLAP limit must be an integer between 1 and 1000");
                }
            }
            if (entry.getValue() instanceof String s && s.length() > 500) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parameter value too long");
            }
        }
    }

    private void requireTenantGrant(String username, String tenantId) {
        Integer granted = jdbc.queryForObject("""
                SELECT COUNT(*) FROM analytics_tenant_grant
                WHERE username = ? AND tenant_id = ? AND active = TRUE
                """, Integer.class, username, tenantId);
        if (granted == null || granted == 0) throw new AccessDeniedException("No analytics grant for requested tenant");
    }

    private void requireScopeGrant(String username, String tenantId, String scopeType) {
        Integer granted = jdbc.queryForObject("""
                SELECT COUNT(*) FROM analytics_scope_grant
                WHERE username = ? AND tenant_id = ? AND scope_type = ? AND active = TRUE
                """, Integer.class, username, tenantId, scopeType);
        if (granted == null || granted == 0) throw new AccessDeniedException("No authorized analytics scope for " + scopeType);
    }

    private void require(Authentication authentication, String action) {
        if (!authorization.isAllowed(authentication, "analytics", action)) {
            throw new AccessDeniedException("Analytics " + action + " access denied");
        }
    }

    private JobView getOwnedJob(String id, String tenantId, String username) {
        List<JobView> jobs = jdbc.query("""
                SELECT id, tenant_id, job_type, dataset_key, status, attempt_count,
                       max_attempts, result_json, error_message, created_at, updated_at
                FROM analytics_job WHERE id = ? AND tenant_id = ? AND requested_by = ?
                """, (rs, row) -> mapJob(rs), id, tenantId, username);
        if (jobs.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Analytics job not found");
        return jobs.getFirst();
    }

    private List<JobView> findJob(String tenantId, String username, String key) {
        return jdbc.query("""
                SELECT id, tenant_id, job_type, dataset_key, status, attempt_count,
                       max_attempts, result_json, error_message, created_at, updated_at
                FROM analytics_job WHERE tenant_id = ? AND requested_by = ? AND idempotency_key = ?
                """, (rs, row) -> mapJob(rs), tenantId, username, key);
    }

    private JobView mapJob(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new JobView(rs.getString("id"), rs.getString("tenant_id"), rs.getString("job_type"),
                rs.getString("dataset_key"), rs.getString("status"), rs.getInt("attempt_count"),
                rs.getInt("max_attempts"), rs.getString("result_json"), rs.getString("error_message"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private record DatasetPolicy(String scopeType, Set<String> jobTypes) {}
    public record JobView(String id, String tenantId, String jobType, String datasetKey, String status,
                          int attemptCount, int maxAttempts, String resultJson, String errorMessage,
                          Instant createdAt, Instant updatedAt) {}
}

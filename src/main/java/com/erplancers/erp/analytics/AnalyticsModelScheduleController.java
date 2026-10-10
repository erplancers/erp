package com.erplancers.erp.analytics;

import com.erplancers.erp.common.security.PolicyAuthorizationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.scheduling.support.CronExpression;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analytics")
@SecurityRequirement(name = "basicAuth")
public class AnalyticsModelScheduleController {
    private static final Map<String, String> SCOPE = Map.of(
            "sales_orders", "CONTACT", "inventory_daily", "WAREHOUSE",
            "order_lines", "CONTACT", "receivables", "CONTACT");
    private static final Map<String, Set<String>> JOBS = Map.of(
            "sales_orders", Set.of("RFM", "COHORT_ANALYSIS", "CUSTOMER_LIFETIME_VALUE",
                    "CUSTOMER_SEGMENTATION", "CUSTOMER_CHURN_RISK", "MODEL_SCORING"),
            "inventory_daily", Set.of("DEMAND_FORECAST", "STOCKOUT_RISK", "INVENTORY_ANOMALY", "MODEL_SCORING"),
            "order_lines", Set.of("MARKET_BASKET", "PRODUCT_RECOMMENDATIONS"),
            "receivables", Set.of("RECEIVABLES_RISK", "MODEL_SCORING"));
    private static final Map<String, Set<String>> PARAMS = Map.ofEntries(
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
            Map.entry("MODEL_SCORING", Set.of("modelName", "modelVersion", "asOfDate", "reviewThreshold")));
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PolicyAuthorizationService authorization;

    @PostMapping("/schedules")
    public ScheduleView create(Authentication auth, @RequestBody CreateScheduleRequest request) {
        require(auth, "WRITE");
        requireTenantGrant(auth.getName(), request.tenantId());
        String dataset = required(request.datasetKey(), "datasetKey", 100);
        String scope = SCOPE.get(dataset);
        String jobType = required(request.jobType(), "jobType", 80).toUpperCase(Locale.ROOT);
        String type = required(request.scheduleType(), "scheduleType", 24).toUpperCase(Locale.ROOT);
        if (scope == null || !JOBS.getOrDefault(dataset, Set.of()).contains(jobType)
                || !Set.of("RETRAIN", "BATCH_SCORE").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported schedule type or job/dataset combination");
        }
        if ("BATCH_SCORE".equals(type) && !"MODEL_SCORING".equals(jobType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BATCH_SCORE requires MODEL_SCORING");
        }
        if ("RETRAIN".equals(type) && Set.of("MODEL_SCORING", "DATA_DRIFT").contains(jobType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RETRAIN requires a training job type");
        }
        requireScopeGrant(auth.getName(), request.tenantId(), scope);
        Map<String, Object> params = request.parameters() == null ? Map.of() : request.parameters();
        if (params.size() > 20 || params.keySet().stream().anyMatch(key -> !PARAMS.getOrDefault(jobType, Set.of()).contains(key))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported job parameters");
        }
        if ("BATCH_SCORE".equals(type)) {
            Object modelName = params.get("modelName");
            Object modelVersion = params.get("modelVersion");
            if (!(modelName instanceof String name) || name.isBlank()
                    || !(modelVersion instanceof Number version) || version.intValue() < 1
                    || version.doubleValue() != version.intValue()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Batch scoring requires modelName and positive integer modelVersion");
            }
        }
        String modelName = required(request.modelName(), "modelName", 160);
        String cron = required(request.cronExpression(), "cronExpression", 120);
        String timezone = request.timezone() == null || request.timezone().isBlank() ? "UTC" : request.timezone();
        try {
            ZoneId zone = ZoneId.of(timezone);
            CronExpression expression = CronExpression.parse(cron);
            ZonedDateTime next = expression.next(ZonedDateTime.now(zone));
            if (next == null) throw new IllegalArgumentException("Cron has no next run");
            String parametersJson = mapper.writeValueAsString(params);
            String id = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO analytics_model_schedule
                      (id, tenant_id, model_name, created_by, schedule_type, job_type, cron_expression,
                       timezone, dataset_key, parameters_json, enabled, next_run_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?)
                    """, id, request.tenantId(), modelName, auth.getName(), type, jobType, cron,
                    timezone, dataset, parametersJson, Timestamp.from(next.toInstant()));
            return scheduleById(id, request.tenantId(), auth.getName());
        } catch (java.time.DateTimeException | IllegalArgumentException | JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cron, timezone, or schedule parameters");
        }
    }

    @GetMapping("/schedules")
    public List<ScheduleView> list(Authentication auth, @RequestParam String tenantId) {
        require(auth, "READ");
        requireTenantGrant(auth.getName(), tenantId);
        return jdbc.query("""
                SELECT id, tenant_id, model_name, created_by, schedule_type, job_type, cron_expression,
                       timezone, dataset_key, parameters_json, enabled, next_run_at, last_run_at, created_at, updated_at
                  FROM analytics_model_schedule
                 WHERE tenant_id = ? AND created_by = ? ORDER BY next_run_at
                """, (rs, row) -> mapSchedule(rs), tenantId, auth.getName());
    }

    @PatchMapping("/schedules/{id}")
    public ScheduleView toggle(Authentication auth, @PathVariable String id, @RequestParam String tenantId,
                               @RequestBody ToggleScheduleRequest request) {
        require(auth, "WRITE");
        requireTenantGrant(auth.getName(), tenantId);
        int changed = jdbc.update("""
                UPDATE analytics_model_schedule SET enabled = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND tenant_id = ? AND created_by = ?
                """, request.enabled(), id, tenantId, auth.getName());
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found");
        return scheduleById(id, tenantId, auth.getName());
    }

    @GetMapping("/models/{name}/{version}/evaluations")
    public List<ModelEvaluationView> evaluations(Authentication auth, @PathVariable String name,
                                                  @PathVariable int version, @RequestParam String tenantId) {
        require(auth, "READ");
        requireTenantGrant(auth.getName(), tenantId);
        return jdbc.query("""
                SELECT id, tenant_id, model_name, model_version, evaluation_type, metrics_json,
                       sample_size, dataset_snapshot, evaluated_by, created_at
                  FROM analytics_model_evaluation
                 WHERE tenant_id = ? AND model_name = ? AND model_version = ?
                   AND (evaluated_by = ? OR EXISTS (
                       SELECT 1 FROM analytics_model_version mv
                        WHERE mv.tenant_id = analytics_model_evaluation.tenant_id
                          AND mv.model_name = analytics_model_evaluation.model_name
                          AND mv.version = analytics_model_evaluation.model_version
                          AND mv.status = 'PRODUCTION'))
                 ORDER BY created_at DESC
                """, (rs, row) -> new ModelEvaluationView(rs.getString("id"), rs.getString("tenant_id"),
                rs.getString("model_name"), rs.getInt("model_version"), rs.getString("evaluation_type"),
                rs.getString("metrics_json"), rs.getLong("sample_size"), rs.getString("dataset_snapshot"),
                rs.getString("evaluated_by"), rs.getTimestamp("created_at").toInstant()),
                tenantId, name, version, auth.getName());
    }

    private ScheduleView scheduleById(String id, String tenantId, String user) {
        List<ScheduleView> rows = jdbc.query("""
                SELECT id, tenant_id, model_name, created_by, schedule_type, job_type, cron_expression,
                       timezone, dataset_key, parameters_json, enabled, next_run_at, last_run_at, created_at, updated_at
                  FROM analytics_model_schedule WHERE id = ? AND tenant_id = ? AND created_by = ?
                """, (rs, row) -> mapSchedule(rs), id, tenantId, user);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found");
        return rows.getFirst();
    }

    private ScheduleView mapSchedule(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ScheduleView(rs.getString("id"), rs.getString("tenant_id"), rs.getString("model_name"),
                rs.getString("created_by"), rs.getString("schedule_type"), rs.getString("job_type"),
                rs.getString("cron_expression"), rs.getString("timezone"), rs.getString("dataset_key"),
                rs.getString("parameters_json"), rs.getBoolean("enabled"), rs.getTimestamp("next_run_at").toInstant(),
                rs.getTimestamp("last_run_at") == null ? null : rs.getTimestamp("last_run_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private void requireTenantGrant(String username, String tenantId) {
        if (tenantId == null || tenantId.isBlank() || tenantId.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId is required");
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM analytics_tenant_grant
                 WHERE username = ? AND tenant_id = ? AND active = TRUE
                """, Integer.class, username, tenantId);
        if (count == null || count == 0) throw new AccessDeniedException("No analytics grant for requested tenant");
    }

    private void requireScopeGrant(String username, String tenantId, String scope) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM analytics_scope_grant
                 WHERE username = ? AND tenant_id = ? AND scope_type = ? AND active = TRUE
                """, Integer.class, username, tenantId, scope);
        if (count == null || count == 0) throw new AccessDeniedException("No authorized scope for scheduled job");
    }

    private void require(Authentication auth, String action) {
        if (auth == null || !auth.isAuthenticated() || !authorization.isAllowed(auth, "analytics", action)) {
            throw new AccessDeniedException("Analytics " + action + " access denied");
        }
    }

    private String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }

    public record CreateScheduleRequest(String tenantId, String modelName, String scheduleType, String jobType,
                                        String cronExpression, String timezone, String datasetKey,
                                        Map<String, Object> parameters) {}
    public record ToggleScheduleRequest(boolean enabled) {}
    public record ScheduleView(String id, String tenantId, String modelName, String createdBy,
                               String scheduleType, String jobType, String cronExpression, String timezone,
                               String datasetKey, String parametersJson, boolean enabled, Instant nextRunAt,
                               Instant lastRunAt, Instant createdAt, Instant updatedAt) {}
    public record ModelEvaluationView(String id, String tenantId, String modelName, int modelVersion,
                                      String evaluationType, String metricsJson, long sampleSize,
                                      String datasetSnapshot, String evaluatedBy, Instant createdAt) {}
}

package com.erplancers.erp.analytics;

import com.erplancers.erp.common.security.PolicyAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Administrative policy API for scheduled retraining; execution remains in the Spark worker queue. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analytics")
public class AnalyticsRetrainingPolicyController {
    private static final Map<String, Set<String>> DATASETS_BY_JOB = Map.of(
            "CUSTOMER_SEGMENTATION", Set.of("sales_orders"),
            "CUSTOMER_CHURN_RISK", Set.of("sales_orders"),
            "DEMAND_FORECAST", Set.of("inventory_daily"),
            "STOCKOUT_RISK", Set.of("inventory_daily"),
            "RECEIVABLES_RISK", Set.of("receivables"),
            "MODEL_SCORING", Set.of("sales_orders", "inventory_daily", "receivables"));
    private final JdbcTemplate jdbc;
    private final PolicyAuthorizationService authorization;
    private final ObjectMapper mapper;

    @GetMapping("/retraining-policies")
    public List<PolicyView> list(Authentication auth, @RequestParam String tenantId) {
        require(auth, "READ");
        tenantGrant(auth.getName(), tenantId);
        return jdbc.query("""
                SELECT tenant_id, model_name, enabled, dataset_key, job_type, parameters_json,
                       cron_expression, min_days_between_runs, last_enqueued_at, updated_by, updated_at
                  FROM analytics_model_retraining_policy WHERE tenant_id = ?
                 ORDER BY model_name LIMIT 200
                """, (rs, n) -> new PolicyView(rs.getString("tenant_id"), rs.getString("model_name"),
                rs.getBoolean("enabled"), rs.getString("dataset_key"), rs.getString("job_type"),
                rs.getString("parameters_json"), rs.getString("cron_expression"),
                rs.getInt("min_days_between_runs"),
                rs.getTimestamp("last_enqueued_at") == null ? null : rs.getTimestamp("last_enqueued_at").toInstant(),
                rs.getString("updated_by"), rs.getTimestamp("updated_at").toInstant()), tenantId);
    }

    @PutMapping("/retraining-policies")
    public PolicyView upsert(Authentication auth, @RequestBody PolicyRequest request) {
        require(auth, "WRITE");
        if (request.tenantId() == null || request.modelName() == null
                || !request.modelName().matches("[A-Za-z0-9_.-]{1,160}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid tenantId or modelName");
        }
        tenantGrant(auth.getName(), request.tenantId());
        String jobType = request.jobType() == null ? "" : request.jobType().toUpperCase();
        Set<String> allowedDatasets = DATASETS_BY_JOB.get(jobType);
        if (allowedDatasets == null || !allowedDatasets.contains(request.datasetKey())
                || request.parameters() == null || request.parameters().size() > 20
                || request.cronExpression() == null || request.cronExpression().length() > 120
                || request.minDaysBetweenRuns() < 1 || request.minDaysBetweenRuns() > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported retraining policy");
        }
        try {
            if (!org.springframework.scheduling.support.CronExpression.isValidExpression(request.cronExpression()))
                throw new IllegalArgumentException("Invalid cron");
            mapper.writeValueAsString(request.parameters());
        } catch (Exception invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cron expression or parameters");
        }
        String scopeType = Set.of("inventory_daily").contains(request.datasetKey()) ? "WAREHOUSE" : "CONTACT";
        Integer scopeGrant = jdbc.queryForObject("""
                SELECT COUNT(*) FROM analytics_scope_grant
                 WHERE username = ? AND tenant_id = ? AND scope_type = ? AND active = TRUE
                """, Integer.class, auth.getName(), request.tenantId(), scopeType);
        if (scopeGrant == null || scopeGrant == 0)
            throw new AccessDeniedException("Analytics scope grant required for retraining policy");
        try {
            jdbc.update("""
                    INSERT INTO analytics_model_retraining_policy
                      (tenant_id, model_name, enabled, dataset_key, job_type, parameters_json,
                       cron_expression, min_days_between_runs, updated_by, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(tenant_id, model_name) DO UPDATE SET enabled=EXCLUDED.enabled,
                      dataset_key=EXCLUDED.dataset_key, job_type=EXCLUDED.job_type,
                      parameters_json=EXCLUDED.parameters_json, cron_expression=EXCLUDED.cron_expression,
                      min_days_between_runs=EXCLUDED.min_days_between_runs, updated_by=EXCLUDED.updated_by,
                      updated_at=CURRENT_TIMESTAMP
                    """, request.tenantId(), request.modelName(), request.enabled(), request.datasetKey(),
                    jobType, mapper.writeValueAsString(request.parameters()), request.cronExpression(),
                    request.minDaysBetweenRuns(), auth.getName());
        } catch (Exception failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to save retraining policy");
        }
        return list(auth, request.tenantId()).stream().filter(p -> p.modelName().equals(request.modelName()))
                .findFirst().orElseThrow();
    }

    private void require(Authentication auth, String permission) {
        if (auth == null || !auth.isAuthenticated() || !authorization.isAllowed(auth, "analytics", permission))
            throw new AccessDeniedException("Analytics " + permission + " access denied");
    }
    private void tenantGrant(String username, String tenantId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM analytics_tenant_grant
                 WHERE username = ? AND tenant_id = ? AND active = TRUE
                """, Integer.class, username, tenantId);
        if (count == null || count == 0) throw new AccessDeniedException("Analytics tenant grant required");
    }

    public record PolicyRequest(String tenantId, String modelName, boolean enabled, String datasetKey,
            String jobType, Map<String, Object> parameters, String cronExpression, int minDaysBetweenRuns) {}
    public record PolicyView(String tenantId, String modelName, boolean enabled, String datasetKey,
            String jobType, String parametersJson, String cronExpression, int minDaysBetweenRuns,
            java.time.Instant lastEnqueuedAt, String updatedBy, java.time.Instant updatedAt) {}
}

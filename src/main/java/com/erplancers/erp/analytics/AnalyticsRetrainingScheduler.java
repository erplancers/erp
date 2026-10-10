package com.erplancers.erp.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Queues approved retraining policies into the existing leased Spark worker queue. */
@Component
@RequiredArgsConstructor
public class AnalyticsRetrainingScheduler {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Scheduled(fixedDelayString = "${analytics.retraining.poll-ms:60000}")
    public void enqueueDuePolicies() {
        List<Policy> policies = jdbc.query("""
                SELECT tenant_id, model_name, dataset_key, job_type, parameters_json, cron_expression,
                       min_days_between_runs, last_enqueued_at, updated_by
                  FROM analytics_model_retraining_policy
                 WHERE enabled = TRUE
                 ORDER BY tenant_id, model_name LIMIT 500
                """, (rs, n) -> new Policy(rs.getString("tenant_id"), rs.getString("model_name"),
                rs.getString("dataset_key"), rs.getString("job_type"), rs.getString("parameters_json"),
                rs.getString("cron_expression"), rs.getInt("min_days_between_runs"),
                rs.getTimestamp("last_enqueued_at") == null ? null : rs.getTimestamp("last_enqueued_at").toInstant(),
                rs.getString("updated_by")));
        Instant now = Instant.now();
        for (Policy policy : policies) {
            try {
                if (isDue(policy, now)) enqueue(policy, now);
            } catch (Exception failure) {
                System.err.println("Retraining policy failed for " + policy.tenantId() + "/" + policy.modelName()
                        + ": " + failure.getMessage());
            }
        }
    }

    private boolean isDue(Policy policy, Instant now) {
        CronExpression cron = CronExpression.parse(policy.cronExpression());
        Instant reference = policy.lastEnqueuedAt() == null
                ? now.minusSeconds(120) : policy.lastEnqueuedAt();
        if (policy.lastEnqueuedAt() != null
                && policy.lastEnqueuedAt().plusSeconds(policy.minDaysBetweenRuns() * 86400L).isAfter(now)) return false;
        var next = cron.next(reference.atZone(ZoneOffset.UTC));
        return next != null && !next.toInstant().isAfter(now);
    }

    private void enqueue(Policy p, Instant now) throws Exception {
        String scopeType = "inventory_daily".equals(p.datasetKey()) ? "WAREHOUSE" : "CONTACT";
        Integer tenantGrant = jdbc.queryForObject("""
                SELECT COUNT(*) FROM analytics_tenant_grant
                 WHERE username = ? AND tenant_id = ? AND active = TRUE
                """, Integer.class, p.updatedBy(), p.tenantId());
        Integer scopeGrant = jdbc.queryForObject("""
                SELECT COUNT(*) FROM analytics_scope_grant
                 WHERE username = ? AND tenant_id = ? AND scope_type = ? AND active = TRUE
                """, Integer.class, p.updatedBy(), p.tenantId(), scopeType);
        if (tenantGrant == null || tenantGrant == 0 || scopeGrant == null || scopeGrant == 0) {
            jdbc.update("""
                    UPDATE analytics_model_retraining_policy SET enabled=FALSE, updated_at=CURRENT_TIMESTAMP
                     WHERE tenant_id=? AND model_name=?
                    """, p.tenantId(), p.modelName());
            return;
        }
        JsonNode params = mapper.readTree(p.parametersJson());
        if (!params.isObject() || params.size() > 20) return;
        String id = UUID.randomUUID().toString();
        String idempotency = "retrain-" + p.modelName() + "-" + now.toEpochMilli();
        try {
            jdbc.update("""
                    INSERT INTO analytics_job
                      (id, tenant_id, requested_by, job_type, dataset_key, scope_type,
                       parameters_json, idempotency_key, status, attempt_count, max_attempts)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', 0, 3)
                    """, id, p.tenantId(), p.updatedBy(), p.jobType(), p.datasetKey(), scopeType,
                    p.parametersJson(), idempotency);
            jdbc.update("""
                    UPDATE analytics_model_retraining_policy SET last_enqueued_at=?, updated_at=CURRENT_TIMESTAMP
                     WHERE tenant_id=? AND model_name=?
                    """, java.sql.Timestamp.from(now), p.tenantId(), p.modelName());
        } catch (DuplicateKeyException duplicate) {
            // Another scheduler replica already enqueued this exact policy tick.
        }
    }

    private record Policy(String tenantId, String modelName, String datasetKey, String jobType,
            String parametersJson, String cronExpression, int minDaysBetweenRuns,
            Instant lastEnqueuedAt, String updatedBy) {}
}

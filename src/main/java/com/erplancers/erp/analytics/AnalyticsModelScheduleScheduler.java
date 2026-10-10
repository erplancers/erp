package com.erplancers.erp.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Queues scheduled retraining and batch-scoring jobs after rechecking live grants. */
@Component
@RequiredArgsConstructor
public class AnalyticsModelScheduleScheduler {
    private static final Map<String, Set<String>> JOBS = Map.of(
            "sales_orders", Set.of("RFM", "COHORT_ANALYSIS", "CUSTOMER_LIFETIME_VALUE",
                    "CUSTOMER_SEGMENTATION", "CUSTOMER_CHURN_RISK", "DATA_DRIFT", "MODEL_SCORING"),
            "inventory_daily", Set.of("DEMAND_FORECAST", "STOCKOUT_RISK", "INVENTORY_ANOMALY", "DATA_DRIFT", "MODEL_SCORING"),
            "order_lines", Set.of("MARKET_BASKET", "PRODUCT_RECOMMENDATIONS"),
            "receivables", Set.of("RECEIVABLES_RISK", "DATA_DRIFT", "MODEL_SCORING"));
    private static final Map<String, String> SCOPE = Map.of(
            "sales_orders", "CONTACT", "inventory_daily", "WAREHOUSE",
            "order_lines", "CONTACT", "receivables", "CONTACT");
    private static final Map<String, Set<String>> PARAMS = Map.ofEntries(
            Map.entry("RFM", Set.of("asOfDate")), Map.entry("COHORT_ANALYSIS", Set.of()),
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
            Map.entry("MODEL_SCORING", Set.of("modelName", "modelVersion", "asOfDate", "reviewThreshold")));

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Scheduled(fixedDelayString = "${analytics.model-scheduler-delay-ms:60000}")
    @Transactional
    public void enqueueDueSchedules() {
        List<Schedule> due = jdbc.query("""
                SELECT id, tenant_id, model_name, created_by, schedule_type, job_type,
                       cron_expression, timezone, dataset_key, parameters_json, next_run_at
                  FROM analytics_model_schedule
                 WHERE enabled = TRUE AND next_run_at <= CURRENT_TIMESTAMP
                 ORDER BY next_run_at LIMIT 50 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new Schedule(rs.getString("id"), rs.getString("tenant_id"),
                rs.getString("model_name"), rs.getString("created_by"), rs.getString("schedule_type"),
                rs.getString("job_type"), rs.getString("cron_expression"), rs.getString("timezone"),
                rs.getString("dataset_key"), rs.getString("parameters_json"),
                rs.getTimestamp("next_run_at")));
        for (Schedule schedule : due) {
            try {
                enqueue(schedule);
            } catch (RuntimeException | java.io.IOException failure) {
                jdbc.update("UPDATE analytics_model_schedule SET enabled = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                        schedule.id());
                jdbc.update("""
                        INSERT INTO analytics_operation_metric
                          (tenant_id, component, operation, status, duration_ms, rows_processed, detail_json)
                        VALUES (?, 'ML_SCHEDULER', 'enqueue_scheduled_job', 'FAILED', 0, 0, ?)
                        """, schedule.tenantId(), "{\"scheduleId\":\"" + schedule.id() + "\"}");
            }
        }
    }

    private void enqueue(Schedule schedule) throws java.io.IOException {
        Set<String> supported = JOBS.get(schedule.datasetKey());
        String scopeType = SCOPE.get(schedule.datasetKey());
        if (supported == null || scopeType == null || !supported.contains(schedule.jobType())) {
            throw new IllegalArgumentException("Unsupported scheduled job/dataset");
        }
        if ("BATCH_SCORE".equals(schedule.scheduleType()) && !"MODEL_SCORING".equals(schedule.jobType())) {
            throw new IllegalArgumentException("Batch-score schedules must use MODEL_SCORING");
        }
        if ("RETRAIN".equals(schedule.scheduleType()) && Set.of("MODEL_SCORING", "DATA_DRIFT").contains(schedule.jobType())) {
            throw new IllegalArgumentException("Retraining schedules must use a training/analysis job");
        }
        JsonNode parameters = mapper.readTree(schedule.parametersJson());
        if (!parameters.isObject()) throw new IllegalArgumentException("Schedule parameters must be an object");
        var names = parameters.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!PARAMS.getOrDefault(schedule.jobType(), Set.of()).contains(name)) {
                throw new IllegalArgumentException("Unsupported parameter for scheduled job: " + name);
            }
        }
        Integer tenantGrant = jdbc.queryForObject("""
                SELECT COUNT(*) FROM analytics_tenant_grant
                 WHERE username = ? AND tenant_id = ? AND active = TRUE
                """, Integer.class, schedule.createdBy(), schedule.tenantId());
        Integer scopeGrant = jdbc.queryForObject("""
                SELECT COUNT(*) FROM analytics_scope_grant
                 WHERE username = ? AND tenant_id = ? AND scope_type = ? AND active = TRUE
                """, Integer.class, schedule.createdBy(), schedule.tenantId(), scopeType);
        if (tenantGrant == null || tenantGrant == 0 || scopeGrant == null || scopeGrant == 0) {
            // Permission revocation pauses the schedule instead of running with stale access.
            jdbc.update("UPDATE analytics_model_schedule SET enabled = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    schedule.id());
            return;
        }

        ZoneId zone = ZoneId.of(schedule.timezone());
        CronExpression cron = CronExpression.parse(schedule.cronExpression());
        ZonedDateTime next = cron.next(ZonedDateTime.now(zone));
        if (next == null) throw new IllegalArgumentException("Schedule has no next execution");
        String idempotencyKey = "schedule-" + schedule.id() + "-" + schedule.nextRunAt().toInstant().toEpochMilli();
        jdbc.update("""
                INSERT INTO analytics_job
                  (id, tenant_id, requested_by, job_type, dataset_key, scope_type, parameters_json,
                   idempotency_key, status, attempt_count, max_attempts)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', 0, 3)
                ON CONFLICT (tenant_id, requested_by, idempotency_key) DO NOTHING
                """, UUID.randomUUID().toString(), schedule.tenantId(), schedule.createdBy(), schedule.jobType(),
                schedule.datasetKey(), scopeType, schedule.parametersJson(), idempotencyKey);
        jdbc.update("""
                UPDATE analytics_model_schedule SET last_run_at = CURRENT_TIMESTAMP, next_run_at = ?,
                       updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, Timestamp.from(next.toInstant()), schedule.id());
        jdbc.update("""
                INSERT INTO analytics_operation_metric
                  (tenant_id, component, operation, status, duration_ms, rows_processed, detail_json)
                VALUES (?, 'ML_SCHEDULER', ?, 'SUCCEEDED', 0, 0, ?)
                """, schedule.tenantId(), schedule.scheduleType().toLowerCase(), "{\"scheduleId\":\"" + schedule.id() + "\"}");
    }

    private record Schedule(String id, String tenantId, String modelName, String createdBy, String scheduleType,
                            String jobType, String cronExpression, String timezone, String datasetKey,
                            String parametersJson, Timestamp nextRunAt) {}
}

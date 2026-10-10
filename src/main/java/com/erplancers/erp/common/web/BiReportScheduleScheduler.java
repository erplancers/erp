package com.erplancers.erp.common.web;

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
import java.util.UUID;

/**
 * Converts due report subscriptions into durable delivery queue rows. Delivery itself is
 * intentionally performed by a separately deployed sender/worker, not on the scheduler thread.
 */
@Component
@RequiredArgsConstructor
public class BiReportScheduleScheduler {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Scheduled(fixedDelayString = "${bi.delivery.scheduler-delay-ms:60000}")
    @Transactional
    public void enqueueDueReports() {
        List<DueSchedule> due = jdbc.query("""
                SELECT id, report_id, tenant_id, created_by, cron_expression, timezone
                  FROM bi_report_schedule
                 WHERE enabled = TRUE AND next_run_at <= CURRENT_TIMESTAMP
                 ORDER BY next_run_at
                 LIMIT 50
                 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new DueSchedule(rs.getString("id"), rs.getString("report_id"),
                rs.getString("tenant_id"), rs.getString("created_by"), rs.getString("cron_expression"),
                rs.getString("timezone")));
        for (DueSchedule schedule : due) {
            try {
                ZoneId zone = ZoneId.of(schedule.timezone());
                CronExpression cron = CronExpression.parse(schedule.cronExpression());
                ZonedDateTime next = cron.next(ZonedDateTime.now(zone));
                if (next == null) {
                    jdbc.update("UPDATE bi_report_schedule SET enabled = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                            schedule.id());
                    continue;
                }
                jdbc.update("""
                        INSERT INTO bi_report_delivery
                          (id, schedule_id, report_id, tenant_id, requested_by, status, attempts, available_at)
                        VALUES (?, ?, ?, ?, ?, 'QUEUED', 0, CURRENT_TIMESTAMP)
                        """, UUID.randomUUID().toString(), schedule.id(), schedule.reportId(),
                        schedule.tenantId(), schedule.createdBy());
                jdbc.update("""
                        UPDATE bi_report_schedule SET last_run_at = CURRENT_TIMESTAMP, next_run_at = ?,
                               updated_at = CURRENT_TIMESTAMP WHERE id = ?
                        """, Timestamp.from(next.toInstant()), schedule.id());
            } catch (RuntimeException invalidSchedule) {
                // A bad timezone or cron is isolated to this subscription; do not block other tenants.
                jdbc.update("UPDATE bi_report_schedule SET enabled = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                        schedule.id());
                jdbc.update("""
                        INSERT INTO analytics_operation_metric
                          (tenant_id, component, operation, status, duration_ms, rows_processed, detail_json)
                        VALUES (?, 'BI_SCHEDULER', 'enqueue_report_delivery', 'FAILED', 0, 0, ?)
                        """, schedule.tenantId(), "{\"scheduleId\":\"" + schedule.id() + "\"}");
            }
        }
    }

    private record DueSchedule(String id, String reportId, String tenantId, String createdBy,
                               String cronExpression, String timezone) {}
}

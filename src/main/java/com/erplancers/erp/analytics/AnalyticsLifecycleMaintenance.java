package com.erplancers.erp.analytics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cache housekeeping and low-cardinality operational metrics for BI/ML capacity monitoring. */
@Component
public class AnalyticsLifecycleMaintenance {
    private final JdbcTemplate jdbc;

    public AnalyticsLifecycleMaintenance(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        gauge(registry, "erp.analytics.jobs.queued", "analytics_job", "QUEUED");
        gauge(registry, "erp.analytics.jobs.running", "analytics_job", "RUNNING");
        gauge(registry, "erp.analytics.models.production", "analytics_model_version", "PRODUCTION");
        Gauge.builder("erp.analytics.subscriptions.active", jdbc,
                db -> count(db, "SELECT COUNT(*) FROM analytics_report_subscription WHERE active = TRUE"))
                .description("Active scheduled BI report subscriptions").register(registry);
        Gauge.builder("erp.analytics.cache.entries", jdbc,
                db -> count(db, "SELECT COUNT(*) FROM analytics_query_cache WHERE expires_at > CURRENT_TIMESTAMP"))
                .description("Unexpired BI query cache entries").register(registry);
    }

    private void gauge(MeterRegistry registry, String name, String table, String status) {
        Gauge.builder(name, jdbc, db -> count(db, "SELECT COUNT(*) FROM " + table + " WHERE status = '" + status + "'"))
                .description("Current analytics records in " + status + " state").register(registry);
    }

    private static double count(JdbcTemplate db, String sql) {
        try {
            Long value = db.queryForObject(sql, Long.class);
            return value == null ? 0 : value.doubleValue();
        } catch (RuntimeException unavailable) {
            return 0;
        }
    }

    @Scheduled(cron = "${analytics.cache.cleanup-cron:0 */10 * * * *}", zone = "UTC")
    public void removeExpiredQueryCache() {
        jdbc.update("DELETE FROM analytics_query_cache WHERE expires_at <= CURRENT_TIMESTAMP");
    }
}

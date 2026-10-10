package com.erplancers.erp.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Daily scheduler for production models with numeric baselines. It fails closed when the
 * model owner's tenant or row-scope grant has been revoked.
 */
@Component
@RequiredArgsConstructor
public class AnalyticsDriftScheduler {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "${analytics.drift.cron:0 30 2 * * *}", zone = "UTC")
    public void enqueueDailyDriftChecks() {
        List<ProductionModel> models = jdbc.query("""
                SELECT tenant_id, model_name, version, manifest_json
                  FROM analytics_model_version
                 WHERE status = 'PRODUCTION'
                 ORDER BY tenant_id, model_name
                """, (rs, row) -> new ProductionModel(rs.getString("tenant_id"),
                rs.getString("model_name"), rs.getInt("version"), rs.getString("manifest_json")));
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        for (ProductionModel model : models) {
            try {
                enqueueIfAuthorized(model, day);
            } catch (RuntimeException | java.io.IOException failure) {
                // One malformed model must not prevent other tenants' jobs being queued.
                System.err.println("Unable to schedule analytics drift check for model "
                        + model.modelName() + " v" + model.version() + ": " + failure.getMessage());
            }
        }
    }

    private void enqueueIfAuthorized(ProductionModel model, LocalDate day) throws java.io.IOException {
        JsonNode manifest = objectMapper.readTree(model.manifestJson());
        String datasetKey = manifest.path("datasetKey").asText("");
        String username = manifest.path("createdBy").asText("");
        String baselineUri = manifest.path("baselineUri").asText("");
        JsonNode featureNode = manifest.path("featureColumns");
        if (datasetKey.isBlank() || username.isBlank() || baselineUri.isBlank()
                || !featureNode.isArray() || featureNode.isEmpty()) return;

        String scopeType = switch (datasetKey) {
            case "sales_orders", "order_lines" -> "CONTACT";
            case "inventory_daily" -> "WAREHOUSE";
            case "receivables" -> "CONTACT";
            default -> "";
        };
        if (scopeType.isBlank()) return;
        Integer tenantGranted = jdbc.queryForObject("""
                SELECT COUNT(*) FROM analytics_tenant_grant
                 WHERE username = ? AND tenant_id = ? AND active = TRUE
                """, Integer.class, username, model.tenantId());
        Integer scopeGranted = jdbc.queryForObject("""
                SELECT COUNT(*) FROM analytics_scope_grant
                 WHERE username = ? AND tenant_id = ? AND scope_type = ? AND active = TRUE
                """, Integer.class, username, model.tenantId(), scopeType);
        if (tenantGranted == null || tenantGranted == 0 || scopeGranted == null || scopeGranted == 0) return;

        List<String> features = new ArrayList<>();
        featureNode.forEach(node -> {
            String value = node.asText("");
            if (value.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) features.add(value);
        });
        if (features.isEmpty() || features.size() != featureNode.size()) return;

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("modelName", model.modelName());
        parameters.put("modelVersion", model.version());
        parameters.put("featureColumns", features);
        parameters.put("alertThreshold", 0.25d);
        String payload = objectMapper.writeValueAsString(parameters);
        String idempotencyKey = "drift-" + day + "-" + model.modelName() + "-v" + model.version();
        try {
            jdbc.update("""
                    INSERT INTO analytics_job
                      (id, tenant_id, requested_by, job_type, dataset_key, scope_type,
                       parameters_json, idempotency_key, status, attempt_count, max_attempts)
                    VALUES (?, ?, ?, 'DATA_DRIFT', ?, ?, ?, ?, 'QUEUED', 0, 3)
                    """, UUID.randomUUID().toString(), model.tenantId(), username, datasetKey,
                    scopeType, payload, idempotencyKey);
        } catch (DuplicateKeyException alreadyQueued) {
            // This date/model/version has already been scheduled; idempotency is enforced by the DB.
        }
    }

    private record ProductionModel(String tenantId, String modelName, int version, String manifestJson) {}
}

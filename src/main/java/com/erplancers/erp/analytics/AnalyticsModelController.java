package com.erplancers.erp.analytics;

import com.erplancers.erp.common.security.PolicyAuthorizationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/** Model registry read API and audited manual model lifecycle operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analytics")
@SecurityRequirement(name = "basicAuth")
public class AnalyticsModelController {
    private final JdbcTemplate jdbc;
    private final PolicyAuthorizationService authorization;
    private final TransactionTemplate transactions;

    @GetMapping("/models")
    public List<ModelView> models(Authentication authentication, @RequestParam String tenantId,
                                  @RequestParam(required = false) String modelName) {
        require(authentication, "READ");
        tenant(authentication.getName(), tenantId);
        String query = """
                SELECT id, tenant_id, model_name, version, status, artifact_uri,
                       manifest_json, metrics_json, created_at, promoted_at, promoted_by
                FROM analytics_model_version WHERE tenant_id = ?
                """ + (modelName == null ? "" : " AND model_name = ?") + " ORDER BY model_name, version DESC LIMIT 200";
        return modelName == null
                ? jdbc.query(query, (rs, n) -> new ModelView(rs.getString("id"), rs.getString("tenant_id"),
                    rs.getString("model_name"), rs.getInt("version"), rs.getString("status"), rs.getString("artifact_uri"),
                    rs.getString("manifest_json"), rs.getString("metrics_json"), rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("promoted_at") == null ? null : rs.getTimestamp("promoted_at").toInstant(),
                    rs.getString("promoted_by")), tenantId)
                : jdbc.query(query, (rs, n) -> new ModelView(rs.getString("id"), rs.getString("tenant_id"),
                    rs.getString("model_name"), rs.getInt("version"), rs.getString("status"), rs.getString("artifact_uri"),
                    rs.getString("manifest_json"), rs.getString("metrics_json"), rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("promoted_at") == null ? null : rs.getTimestamp("promoted_at").toInstant(),
                    rs.getString("promoted_by")), tenantId, modelName);
    }

    @GetMapping("/experiments")
    public List<ExperimentView> experiments(Authentication authentication, @RequestParam String tenantId) {
        require(authentication, "READ");
        tenant(authentication.getName(), tenantId);
        return jdbc.query("""
                SELECT id, run_name, algorithm, parameters_json, metrics_json, input_snapshot, status, created_by, created_at
                FROM analytics_experiment WHERE tenant_id = ? AND created_by = ?
                ORDER BY created_at DESC LIMIT 100
                """, (rs, n) -> new ExperimentView(rs.getString("id"), rs.getString("run_name"),
                rs.getString("algorithm"), rs.getString("parameters_json"), rs.getString("metrics_json"),
                rs.getString("input_snapshot"), rs.getString("status"), rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant()), tenantId, authentication.getName());
    }

    @GetMapping("/drift")
    public List<DriftView> drift(Authentication authentication, @RequestParam String tenantId,
                                 @RequestParam(required = false) String modelName) {
        require(authentication, "READ");
        tenant(authentication.getName(), tenantId);
        String query = """
                SELECT id, model_name, model_version, feature_name, population_stability_index,
                       sample_size, baseline_snapshot, current_snapshot, measured_at, status
                FROM analytics_drift_metric WHERE tenant_id = ?
                """ + (modelName == null ? "" : " AND model_name = ?") + " ORDER BY measured_at DESC LIMIT 500";
        return modelName == null
                ? jdbc.query(query, (rs, n) -> mapDrift(rs), tenantId)
                : jdbc.query(query, (rs, n) -> mapDrift(rs), tenantId, modelName);
    }

    @PostMapping("/models/{modelName}/{version}/review")
    public ModelView review(Authentication authentication, @RequestParam String tenantId,
                            @PathVariable String modelName, @PathVariable int version,
                            @Valid @RequestBody ModelReviewRequest request) {
        require(authentication, "MODEL_APPROVE");
        tenant(authentication.getName(), tenantId);
        transactions.executeWithoutResult(status -> {
            String current = modelStatusForUpdate(tenantId, modelName, version);
            if (!"CANDIDATE".equals(current)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only CANDIDATE models can be reviewed");
            String target = request.approved() ? "VALIDATED" : "REJECTED";
            jdbc.update("""
                    UPDATE analytics_model_version SET status = ?
                    WHERE tenant_id = ? AND model_name = ? AND version = ? AND status = 'CANDIDATE'
                    """, target, tenantId, modelName, version);
            insertReview(tenantId, modelName, version, "VALIDATE", target, authentication.getName(), request.note());
        });
        return model(tenantId, modelName, version);
    }

    @PostMapping("/models/{modelName}/{version}/promote")
    public ModelView promote(Authentication authentication, @RequestParam String tenantId,
                             @PathVariable String modelName, @PathVariable int version,
                             @Valid @RequestBody(required = false) ModelReviewNote request) {
        require(authentication, "MODEL_PROMOTE");
        tenant(authentication.getName(), tenantId);
        transitionModel(tenantId, modelName, version, "VALIDATED", authentication.getName(),
                request == null ? "" : request.note(), "PROMOTE");
        return model(tenantId, modelName, version);
    }

    @PostMapping("/models/{modelName}/{version}/rollback")
    public ModelView rollback(Authentication authentication, @RequestParam String tenantId,
                              @PathVariable String modelName, @PathVariable int version,
                              @Valid @RequestBody(required = false) ModelReviewNote request) {
        require(authentication, "MODEL_ROLLBACK");
        tenant(authentication.getName(), tenantId);
        transitionModel(tenantId, modelName, version, "RETIRED", authentication.getName(),
                request == null ? "" : request.note(), "ROLLBACK");
        return model(tenantId, modelName, version);
    }

    private void transitionModel(String tenantId, String modelName, int version, String requiredStatus,
                                 String reviewer, String note, String action) {
        transactions.executeWithoutResult(transaction -> {
            // Lock every version row for this tenant/model to serialize promotion and rollback.
            jdbc.query("SELECT id FROM analytics_model_version WHERE tenant_id = ? AND model_name = ? ORDER BY version FOR UPDATE",
                    (rs, n) -> rs.getString(1), tenantId, modelName);
            String current = modelStatusForUpdate(tenantId, modelName, version);
            if (!requiredStatus.equals(current)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Target model must be " + requiredStatus + "; current state is " + current);
            }
            jdbc.update("""
                    UPDATE analytics_model_version SET status = 'RETIRED', retired_at = CURRENT_TIMESTAMP
                    WHERE tenant_id = ? AND model_name = ? AND status = 'PRODUCTION'
                    """, tenantId, modelName);
            int changed = jdbc.update("""
                    UPDATE analytics_model_version
                       SET status = 'PRODUCTION', promoted_at = CURRENT_TIMESTAMP,
                           promoted_by = ?, retired_at = NULL
                     WHERE tenant_id = ? AND model_name = ? AND version = ? AND status = ?
                    """, reviewer, tenantId, modelName, version, requiredStatus);
            if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Model state changed concurrently");
            insertReview(tenantId, modelName, version, action, "PRODUCTION", reviewer, note);
        });
    }

    private String modelStatusForUpdate(String tenantId, String modelName, int version) {
        List<String> statuses = jdbc.query("""
                SELECT status FROM analytics_model_version
                WHERE tenant_id = ? AND model_name = ? AND version = ? FOR UPDATE
                """, (rs, n) -> rs.getString(1), tenantId, modelName, version);
        if (statuses.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Model version not found");
        return statuses.getFirst();
    }

    private ModelView model(String tenantId, String modelName, int version) {
        List<ModelView> rows = jdbc.query("""
                SELECT id, tenant_id, model_name, version, status, artifact_uri,
                       manifest_json, metrics_json, created_at, promoted_at, promoted_by
                FROM analytics_model_version WHERE tenant_id = ? AND model_name = ? AND version = ?
                """, (rs, n) -> new ModelView(rs.getString("id"), rs.getString("tenant_id"),
                rs.getString("model_name"), rs.getInt("version"), rs.getString("status"), rs.getString("artifact_uri"),
                rs.getString("manifest_json"), rs.getString("metrics_json"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("promoted_at") == null ? null : rs.getTimestamp("promoted_at").toInstant(),
                rs.getString("promoted_by")), tenantId, modelName, version);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Model version not found");
        return rows.getFirst();
    }

    private DriftView mapDrift(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DriftView(rs.getLong("id"), rs.getString("model_name"), rs.getInt("model_version"),
                rs.getString("feature_name"), rs.getDouble("population_stability_index"), rs.getLong("sample_size"),
                rs.getString("baseline_snapshot"), rs.getString("current_snapshot"), rs.getTimestamp("measured_at").toInstant(),
                rs.getString("status"));
    }

    private void insertReview(String tenantId, String modelName, int version, String action,
                              String toStatus, String reviewer, String note) {
        jdbc.update("""
                INSERT INTO analytics_model_review
                  (id, tenant_id, model_name, model_version, action, to_status, reviewer, review_note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, java.util.UUID.randomUUID().toString(), tenantId, modelName, version, action, toStatus,
                reviewer, note == null ? "" : note.substring(0, Math.min(note.length(), 1000)));
    }

    private void tenant(String username, String tenantId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM analytics_tenant_grant
                WHERE username = ? AND tenant_id = ? AND active = TRUE
                """, Integer.class, username, tenantId);
        if (count == null || count == 0) throw new AccessDeniedException("No analytics grant for requested tenant");
    }

    private void require(Authentication authentication, String action) {
        if (!authorization.isAllowed(authentication, "analytics", action)) {
            throw new AccessDeniedException("Analytics " + action + " access denied");
        }
    }

    public record ModelView(String id, String tenantId, String modelName, int version, String status,
                            String artifactUri, String manifestJson, String metricsJson, Instant createdAt,
                            Instant promotedAt, String promotedBy) {}
    public record ExperimentView(String id, String runName, String algorithm, String parametersJson,
                                 String metricsJson, String inputSnapshot, String status, String createdBy,
                                 Instant createdAt) {}
    public record DriftView(long id, String modelName, int modelVersion, String featureName, double psi,
                            long sampleSize, String baselineSnapshot, String currentSnapshot, Instant measuredAt,
                            String status) {}
    public record ModelReviewRequest(boolean approved, @Size(max = 1000) String note) {}
    public record ModelReviewNote(@NotBlank @Size(max = 1000) String note) {}
}

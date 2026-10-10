package com.erplancers.erp.analytics;

import java.sql.*;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** PostgreSQL-backed durable queue, experiment log and versioned model registry. */
public final class AnalyticsJobStore {
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public AnalyticsJobStore(String jdbcUrl, String username, String password) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql:")) {
            throw new IllegalArgumentException("A PostgreSQL JDBC URL is required");
        }
        if (username == null || username.isBlank() || password == null) {
            throw new IllegalArgumentException("Worker database credentials are required");
        }
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public record Job(String id, String tenantId, String requestedBy, String jobType, String datasetKey,
                      String scopeType, String parametersJson, int attemptCount, int maxAttempts,
                      Instant createdAt, String workerId) {}
    public record ModelVersion(String id, String tenantId, String modelName, int version,
                              String status, String artifactUri, String manifestJson, String metricsJson) {}

    private Connection connect() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("ApplicationName", "erplancers-spark-analytics-worker");
        properties.setProperty("readOnly", "false"); // queue metadata is updated; extraction uses a separate read-only identity.
        return DriverManager.getConnection(jdbcUrl, properties);
    }

    /** One worker owns a row lease; SKIP LOCKED allows multiple workers without duplicate claims. */
    public Job claimNext(String workerId, int leaseSeconds) throws SQLException {
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("workerId is required");
        if (leaseSeconds < 60 || leaseSeconds > 7200) throw new IllegalArgumentException("leaseSeconds must be in [60,7200]");
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement recover = c.prepareStatement("""
                        UPDATE analytics_job
                           SET status = CASE
                               WHEN status = 'CANCEL_REQUESTED' THEN 'CANCELLED'
                               WHEN attempt_count >= max_attempts THEN 'FAILED'
                               ELSE 'QUEUED' END,
                               worker_id = NULL, lease_until = NULL,
                               available_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                         WHERE status IN ('RUNNING', 'CANCEL_REQUESTED')
                           AND lease_until < CURRENT_TIMESTAMP
                        """)) {
                    recover.executeUpdate();
                }
                String id = null;
                Job selected = null;
                try (PreparedStatement select = c.prepareStatement("""
                        SELECT id, tenant_id, requested_by, job_type, dataset_key, scope_type,
                               parameters_json, attempt_count, max_attempts, created_at
                          FROM analytics_job
                         WHERE status = 'QUEUED' AND available_at <= CURRENT_TIMESTAMP
                         ORDER BY created_at
                         LIMIT 1 FOR UPDATE SKIP LOCKED
                        """);
                     ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        id = rs.getString("id");
                        selected = new Job(id, rs.getString("tenant_id"), rs.getString("requested_by"),
                                rs.getString("job_type"), rs.getString("dataset_key"), rs.getString("scope_type"),
                                rs.getString("parameters_json"), rs.getInt("attempt_count") + 1,
                                rs.getInt("max_attempts"), rs.getTimestamp("created_at").toInstant(), workerId);
                    }
                }
                if (selected == null) {
                    c.commit();
                    return null;
                }
                try (PreparedStatement update = c.prepareStatement("""
                        UPDATE analytics_job SET status = 'RUNNING', worker_id = ?,
                               attempt_count = attempt_count + 1,
                               lease_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                               updated_at = CURRENT_TIMESTAMP
                         WHERE id = ? AND status = 'QUEUED'
                        """)) {
                    update.setString(1, workerId);
                    update.setInt(2, leaseSeconds);
                    update.setString(3, id);
                    if (update.executeUpdate() != 1) throw new SQLException("Unable to claim locked analytics job");
                }
                c.commit();
                return selected;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public boolean cancellationRequested(Job job) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT status FROM analytics_job WHERE id = ? AND worker_id = ?")) {
            ps.setString(1, job.id());
            // worker id isn't part of the Job to avoid leaking worker internals; status is safely queried by ID here.
            ps.setString(2, job.workerId());
            try (ResultSet rs = ps.executeQuery()) {
                return !rs.next() || "CANCEL_REQUESTED".equals(rs.getString(1));
            }
        }
    }

    public void complete(Job job, String workerId, String resultJson) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("""
                UPDATE analytics_job
                   SET status = CASE WHEN status = 'CANCEL_REQUESTED' THEN 'CANCELLED' ELSE 'SUCCEEDED' END,
                       result_json = ?, error_message = NULL, lease_until = NULL, worker_id = NULL,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND worker_id = ? AND status IN ('RUNNING','CANCEL_REQUESTED')
                """)) {
            ps.setString(1, resultJson);
            ps.setString(2, job.id());
            ps.setString(3, workerId);
            if (ps.executeUpdate() != 1) throw new SQLException("Job lease lost before completion: " + job.id());
        }
    }

    public void fail(Job job, String workerId, Throwable failure) throws SQLException {
        fail(job, workerId, failure, true);
    }

    public void fail(Job job, String workerId, Throwable failure, boolean retryable) throws SQLException {
        String message = failure == null ? "Unknown analytics job failure" : failure.getClass().getSimpleName()
                + ": " + String.valueOf(failure.getMessage());
        if (message.length() > 3900) message = message.substring(0, 3900);
        boolean retry = retryable && job.attemptCount() < job.maxAttempts();
        int delaySeconds = (int) Math.min(900, 15L * (1L << Math.min(6, Math.max(0, job.attemptCount() - 1))));
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("""
                UPDATE analytics_job
                   SET status = CASE WHEN status = 'CANCEL_REQUESTED' THEN 'CANCELLED' ELSE ? END,
                       error_message = ?, lease_until = NULL, worker_id = NULL,
                       available_at = CASE WHEN ? THEN CURRENT_TIMESTAMP + (? * INTERVAL '1 second') ELSE available_at END,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND worker_id = ? AND status IN ('RUNNING','CANCEL_REQUESTED')
                """)) {
            ps.setString(1, retry ? "QUEUED" : "FAILED");
            ps.setString(2, message);
            ps.setBoolean(3, retry);
            ps.setInt(4, delaySeconds);
            ps.setString(5, job.id());
            ps.setString(6, workerId);
            ps.executeUpdate();
        }
    }

    public void recordExperiment(Job job, String algorithm, String parametersJson,
                                 String metricsJson, String inputSnapshot, String status) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO analytics_experiment
                  (id, tenant_id, run_name, algorithm, parameters_json, metrics_json,
                   input_snapshot, status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, job.tenantId());
            ps.setString(3, job.jobType() + "-" + job.id());
            ps.setString(4, algorithm);
            ps.setString(5, parametersJson);
            ps.setString(6, metricsJson);
            ps.setString(7, inputSnapshot);
            ps.setString(8, status);
            ps.setString(9, job.requestedBy());
            ps.executeUpdate();
        }
    }

    /** Candidate versions are immutable artifacts and never become live automatically. */
    public ModelVersion registerCandidate(Job job, String modelName, String artifactUri,
                                          String manifestJson, String metricsJson) throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement lock = c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                    lock.setString(1, job.tenantId() + ":" + modelName);
                    lock.executeQuery().close();
                }
                int version;
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT COALESCE(MAX(version), 0) + 1 FROM analytics_model_version
                        WHERE tenant_id = ? AND model_name = ?
                        """)) {
                    ps.setString(1, job.tenantId());
                    ps.setString(2, modelName);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); version = rs.getInt(1); }
                }
                String id = UUID.randomUUID().toString();
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO analytics_model_version
                          (id, tenant_id, model_name, version, status, artifact_uri, manifest_json, metrics_json)
                        VALUES (?, ?, ?, ?, 'CANDIDATE', ?, ?, ?)
                        """)) {
                    ps.setString(1, id); ps.setString(2, job.tenantId()); ps.setString(3, modelName);
                    ps.setInt(4, version); ps.setString(5, artifactUri);
                    ps.setString(6, manifestJson); ps.setString(7, metricsJson); ps.executeUpdate();
                }
                c.commit();
                return new ModelVersion(id, job.tenantId(), modelName, version, "CANDIDATE", artifactUri, manifestJson, metricsJson);
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** An approved reviewer must validate a candidate before promotion. */
    public void reviewModel(String tenantId, String modelName, int version, boolean approved,
                            String reviewer, String note) throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                String next = approved ? "VALIDATED" : "REJECTED";
                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE analytics_model_version SET status = ?, manifest_json = manifest_json
                        WHERE tenant_id = ? AND model_name = ? AND version = ? AND status = 'CANDIDATE'
                        """)) {
                    ps.setString(1, next); ps.setString(2, tenantId); ps.setString(3, modelName); ps.setInt(4, version);
                    if (ps.executeUpdate() != 1) throw new SQLException("Only a candidate model can be reviewed");
                }
                insertReview(c, tenantId, modelName, version, "VALIDATE", approved ? "VALIDATED" : "REJECTED", reviewer, note);
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        }
    }

    public void promoteModel(String tenantId, String modelName, int version, String reviewer, String note) throws SQLException {
        transitionProduction(tenantId, modelName, version, reviewer, note, false);
    }

    public void rollbackModel(String tenantId, String modelName, int version, String reviewer, String note) throws SQLException {
        transitionProduction(tenantId, modelName, version, reviewer, note, true);
    }

    private void transitionProduction(String tenantId, String modelName, int version,
                                      String reviewer, String note, boolean rollback) throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement lock = c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                    lock.setString(1, tenantId + ":" + modelName);
                    lock.executeQuery().close();
                }
                String required = rollback ? "RETIRED" : "VALIDATED";
                try (PreparedStatement target = c.prepareStatement("""
                        SELECT id FROM analytics_model_version
                        WHERE tenant_id = ? AND model_name = ? AND version = ? AND status = ?
                        FOR UPDATE
                        """)) {
                    target.setString(1, tenantId); target.setString(2, modelName);
                    target.setInt(3, version); target.setString(4, required);
                    try (ResultSet rs = target.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Target model must be in " + required + " state");
                    }
                }
                try (PreparedStatement retire = c.prepareStatement("""
                        UPDATE analytics_model_version SET status = 'RETIRED', retired_at = CURRENT_TIMESTAMP
                        WHERE tenant_id = ? AND model_name = ? AND status = 'PRODUCTION'
                        """)) {
                    retire.setString(1, tenantId); retire.setString(2, modelName); retire.executeUpdate();
                }
                try (PreparedStatement activate = c.prepareStatement("""
                        UPDATE analytics_model_version
                           SET status = 'PRODUCTION', promoted_at = CURRENT_TIMESTAMP,
                               promoted_by = ?, retired_at = NULL
                         WHERE tenant_id = ? AND model_name = ? AND version = ? AND status = ?
                        """)) {
                    activate.setString(1, reviewer); activate.setString(2, tenantId); activate.setString(3, modelName);
                    activate.setInt(4, version); activate.setString(5, required);
                    if (activate.executeUpdate() != 1) throw new SQLException("Model state changed during promotion");
                }
                insertReview(c, tenantId, modelName, version, rollback ? "ROLLBACK" : "PROMOTE",
                        "PRODUCTION", reviewer, note);
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        }
    }

    private void insertReview(Connection c, String tenantId, String modelName, int version,
                              String action, String toStatus, String reviewer, String note) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO analytics_model_review
                  (id, tenant_id, model_name, model_version, action, to_status, reviewer, review_note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, UUID.randomUUID().toString()); ps.setString(2, tenantId);
            ps.setString(3, modelName); ps.setInt(4, version); ps.setString(5, action);
            ps.setString(6, toStatus); ps.setString(7, reviewer);
            ps.setString(8, note == null ? "" : note.substring(0, Math.min(note.length(), 1000)));
            ps.executeUpdate();
        }
    }

    public ModelVersion findModelVersion(String tenantId, String modelName, int version) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("""
                SELECT id, tenant_id, model_name, version, status, artifact_uri, manifest_json, metrics_json
                FROM analytics_model_version
                WHERE tenant_id = ? AND model_name = ? AND version = ?
                """)) {
            ps.setString(1, tenantId); ps.setString(2, modelName); ps.setInt(3, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new ModelVersion(rs.getString("id"), rs.getString("tenant_id"), rs.getString("model_name"),
                        rs.getInt("version"), rs.getString("status"), rs.getString("artifact_uri"),
                        rs.getString("manifest_json"), rs.getString("metrics_json"));
            }
        }
    }

    public void recordDrift( Job job, AnalyticsJobHandler.DriftObservation observation) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO analytics_drift_metric
                  (tenant_id, model_name, model_version, feature_name, population_stability_index,
                   sample_size, baseline_snapshot, current_snapshot, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, job.tenantId()); ps.setString(2, observation.modelName());
            ps.setInt(3, observation.modelVersion()); ps.setString(4, observation.feature());
            ps.setDouble(5, observation.psi()); ps.setLong(6, observation.currentRows());
            ps.setString(7, observation.baselineSnapshot()); ps.setString(8, observation.currentSnapshot());
            ps.setString(9, observation.status()); ps.executeUpdate();
        }
    }


    public boolean renewLease(Job job, int leaseSeconds) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("""
                UPDATE analytics_job SET lease_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND worker_id = ? AND status IN ('RUNNING','CANCEL_REQUESTED')
                """)) {
            ps.setInt(1, leaseSeconds);
            ps.setString(2, job.id());
            ps.setString(3, job.workerId());
            return ps.executeUpdate() == 1;
        }
    }

}

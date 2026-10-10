-- Enterprise BI report persistence, cache, delivery and retraining metadata.
CREATE TABLE IF NOT EXISTS analytics_saved_report (
    id VARCHAR(36) PRIMARY KEY,
    owner_username VARCHAR(120) NOT NULL,
    resource_slug VARCHAR(180) NOT NULL,
    report_name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    query_json TEXT NOT NULL,
    calculated_measures_json TEXT NOT NULL DEFAULT '[]',
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_analytics_saved_report_visibility CHECK (visibility IN ('PRIVATE','SHARED'))
);
CREATE INDEX IF NOT EXISTS ix_analytics_saved_report_owner
    ON analytics_saved_report(owner_username, updated_at DESC);

CREATE TABLE IF NOT EXISTS analytics_saved_report_grant (
    report_id VARCHAR(36) NOT NULL REFERENCES analytics_saved_report(id) ON DELETE CASCADE,
    username VARCHAR(120) NOT NULL,
    permission VARCHAR(20) NOT NULL DEFAULT 'READ',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (report_id, username),
    CONSTRAINT ck_analytics_report_grant_permission CHECK (permission IN ('READ','EDIT'))
);

CREATE TABLE IF NOT EXISTS analytics_report_subscription (
    id VARCHAR(36) PRIMARY KEY,
    report_id VARCHAR(36) NOT NULL REFERENCES analytics_saved_report(id) ON DELETE CASCADE,
    owner_username VARCHAR(120) NOT NULL,
    recipient_email VARCHAR(320) NOT NULL,
    cron_expression VARCHAR(120) NOT NULL,
    export_format VARCHAR(10) NOT NULL DEFAULT 'CSV',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    next_run_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_run_at TIMESTAMP,
    last_status VARCHAR(30),
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_analytics_subscription_format CHECK (export_format IN ('CSV','XLSX','PDF'))
);
CREATE INDEX IF NOT EXISTS ix_analytics_subscription_due
    ON analytics_report_subscription(active, next_run_at);

CREATE TABLE IF NOT EXISTS analytics_query_cache (
    cache_key VARCHAR(64) PRIMARY KEY,
    owner_username VARCHAR(120) NOT NULL,
    resource_slug VARCHAR(180) NOT NULL,
    query_hash VARCHAR(64) NOT NULL,
    result_json TEXT NOT NULL,
    row_count INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_analytics_query_cache_expiry ON analytics_query_cache(expires_at);

CREATE TABLE IF NOT EXISTS analytics_refresh_state (
    resource_slug VARCHAR(180) PRIMARY KEY,
    watermark_value VARCHAR(200),
    refreshed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(30) NOT NULL DEFAULT 'READY',
    detail VARCHAR(1000)
);

CREATE TABLE IF NOT EXISTS analytics_model_retraining_policy (
    tenant_id VARCHAR(160) NOT NULL,
    model_name VARCHAR(160) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    dataset_key VARCHAR(100) NOT NULL,
    job_type VARCHAR(80) NOT NULL,
    parameters_json TEXT NOT NULL,
    cron_expression VARCHAR(120) NOT NULL DEFAULT '0 0 3 * * SUN',
    min_days_between_runs INTEGER NOT NULL DEFAULT 7,
    last_enqueued_at TIMESTAMP,
    updated_by VARCHAR(120) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, model_name),
    CONSTRAINT ck_analytics_retrain_interval CHECK (min_days_between_runs BETWEEN 1 AND 365)
);

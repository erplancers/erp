-- BI workspaces, governed calculated measures, delivery scheduling, cache/refresh state,
-- analytics observability, and explicit ML lifecycle metadata.
CREATE TABLE IF NOT EXISTS bi_saved_report (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(160) NOT NULL,
    owner_username VARCHAR(120) NOT NULL,
    name VARCHAR(200) NOT NULL,
    resource_slug VARCHAR(160) NOT NULL,
    definition_json TEXT NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_bi_report_visibility CHECK (visibility IN ('PRIVATE','SHARED')),
    CONSTRAINT ck_bi_report_version CHECK (version > 0),
    CONSTRAINT uq_bi_report_name_owner UNIQUE (tenant_id, owner_username, name)
);
CREATE INDEX IF NOT EXISTS ix_bi_saved_report_tenant ON bi_saved_report(tenant_id, updated_at);

CREATE TABLE IF NOT EXISTS bi_report_share (
    report_id VARCHAR(36) NOT NULL REFERENCES bi_saved_report(id) ON DELETE CASCADE,
    principal_type VARCHAR(16) NOT NULL,
    principal_name VARCHAR(160) NOT NULL,
    permission VARCHAR(16) NOT NULL DEFAULT 'VIEW',
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (report_id, principal_type, principal_name),
    CONSTRAINT ck_bi_share_principal CHECK (principal_type IN ('USER','ROLE')),
    CONSTRAINT ck_bi_share_permission CHECK (permission IN ('VIEW','EDIT','ADMIN'))
);

CREATE TABLE IF NOT EXISTS bi_calculated_measure (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(160) NOT NULL,
    owner_username VARCHAR(120) NOT NULL,
    resource_slug VARCHAR(160) NOT NULL,
    measure_key VARCHAR(80) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    expression VARCHAR(1000) NOT NULL,
    format_hint VARCHAR(24) NOT NULL DEFAULT 'NUMBER',
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_bi_measure_format CHECK (format_hint IN ('NUMBER','PERCENT','CURRENCY','INTEGER')),
    CONSTRAINT ck_bi_measure_visibility CHECK (visibility IN ('PRIVATE','SHARED')),
    CONSTRAINT uq_bi_measure_owner_key UNIQUE (tenant_id, owner_username, resource_slug, measure_key)
);
CREATE INDEX IF NOT EXISTS ix_bi_measure_lookup ON bi_calculated_measure(tenant_id, resource_slug);

CREATE TABLE IF NOT EXISTS bi_report_schedule (
    id VARCHAR(36) PRIMARY KEY,
    report_id VARCHAR(36) NOT NULL REFERENCES bi_saved_report(id) ON DELETE CASCADE,
    tenant_id VARCHAR(160) NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    cron_expression VARCHAR(120) NOT NULL,
    timezone VARCHAR(80) NOT NULL DEFAULT 'UTC',
    recipients_json TEXT NOT NULL,
    export_format VARCHAR(12) NOT NULL DEFAULT 'PDF',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    next_run_at TIMESTAMP NOT NULL,
    last_run_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_bi_schedule_format CHECK (export_format IN ('CSV','XLSX','PDF'))
);
CREATE INDEX IF NOT EXISTS ix_bi_schedule_due ON bi_report_schedule(enabled, next_run_at);

CREATE TABLE IF NOT EXISTS bi_report_delivery (
    id VARCHAR(36) PRIMARY KEY,
    schedule_id VARCHAR(36) NOT NULL REFERENCES bi_report_schedule(id) ON DELETE CASCADE,
    report_id VARCHAR(36) NOT NULL REFERENCES bi_saved_report(id) ON DELETE CASCADE,
    tenant_id VARCHAR(160) NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    error_message VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_bi_delivery_status CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS ix_bi_delivery_queue ON bi_report_delivery(status, available_at, created_at);

CREATE TABLE IF NOT EXISTS bi_query_cache (
    cache_key VARCHAR(128) PRIMARY KEY,
    tenant_id VARCHAR(160) NOT NULL,
    resource_slug VARCHAR(160) NOT NULL,
    payload_json TEXT NOT NULL,
    row_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_bi_query_cache_expiry ON bi_query_cache(expires_at);

CREATE TABLE IF NOT EXISTS bi_refresh_state (
    tenant_id VARCHAR(160) NOT NULL,
    dataset_key VARCHAR(160) NOT NULL,
    watermark_value VARCHAR(500),
    last_started_at TIMESTAMP,
    last_completed_at TIMESTAMP,
    rows_processed BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    error_message VARCHAR(2000),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, dataset_key),
    CONSTRAINT ck_bi_refresh_status CHECK (status IN ('IDLE','RUNNING','SUCCEEDED','FAILED'))
);

CREATE TABLE IF NOT EXISTS analytics_operation_metric (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tenant_id VARCHAR(160),
    component VARCHAR(80) NOT NULL,
    operation VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    duration_ms BIGINT NOT NULL,
    rows_processed BIGINT NOT NULL DEFAULT 0,
    detail_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_analytics_operation_metric_time ON analytics_operation_metric(component, created_at);

CREATE TABLE IF NOT EXISTS analytics_model_evaluation (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(160) NOT NULL,
    model_name VARCHAR(160) NOT NULL,
    model_version INTEGER NOT NULL,
    evaluation_type VARCHAR(40) NOT NULL,
    metrics_json TEXT NOT NULL,
    sample_size BIGINT NOT NULL,
    dataset_snapshot VARCHAR(500),
    evaluated_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_analytics_model_evaluation ON analytics_model_evaluation(tenant_id, model_name, model_version, created_at);

CREATE TABLE IF NOT EXISTS analytics_model_schedule (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(160) NOT NULL,
    model_name VARCHAR(160) NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    schedule_type VARCHAR(24) NOT NULL,
    cron_expression VARCHAR(120) NOT NULL,
    dataset_key VARCHAR(100) NOT NULL,
    parameters_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    next_run_at TIMESTAMP NOT NULL,
    last_run_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_analytics_model_schedule_type CHECK (schedule_type IN ('RETRAIN','BATCH_SCORE'))
);
CREATE INDEX IF NOT EXISTS ix_analytics_model_schedule_due ON analytics_model_schedule(enabled, next_run_at);

-- agentstart-workflow schema (portable across H2 and MySQL).
-- Table names aligned with spring-agent-start:
--   goodle_workflows       ← was agent_workflow
--   goodle_workflow_runs   ← was agent_workflow_run

-- goodle_workflows carries app-scoped drafts + published snapshots (Dify parity).
--
-- Invariant: for a workflow-mode app, exactly one row has {id = app.id,
-- version = 'draft'} — the mutable working copy. Publishing copies its
-- graph_json into a *new* row with a fresh id + timestamp-shaped version and
-- points goodle_apps.workflow_id at that snapshot; the draft row keeps its id so
-- subsequent edits always know where to write.
--
-- Standalone (app_id = null) goodle_workflows still work for the /goodle_workflows debug
-- endpoints so the JSON playground doesn't need an app.
CREATE TABLE IF NOT EXISTS goodle_workflows (
    id                      VARCHAR(64)  NOT NULL,
    tenant_id               VARCHAR(64)  NOT NULL DEFAULT 'default',
    app_id                  VARCHAR(64),
    name                    VARCHAR(255),
    description             TEXT,
    mode                    VARCHAR(32),
    graph                   TEXT,
    -- 'draft' or a snapshot label like '1.0' / a timestamp.
    version                 VARCHAR(32) DEFAULT 'draft',
    published               BOOLEAN DEFAULT FALSE,
    features                TEXT,
    environment_variables   TEXT,
    conversation_variables  TEXT,
    -- Durable contract: START inputs, END outputs, and every node's declared outputs.
    output                  TEXT,
    marked_name             VARCHAR(255),
    marked_comment          VARCHAR(1024),
    created_at              TIMESTAMP,
    updated_at              TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_workflow_app ON goodle_workflows (app_id, version);
CREATE INDEX IF NOT EXISTS idx_workflow_tenant ON goodle_workflows (tenant_id);

CREATE TABLE IF NOT EXISTS goodle_workflow_runs (
    id              VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    workflow_id     VARCHAR(64),
    conversation_id VARCHAR(255),
    status          VARCHAR(32),
    inputs_json     TEXT,
    outputs_json    TEXT,
    steps_json      TEXT,
    error           TEXT,
    elapsed_millis  BIGINT,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_run_workflow ON goodle_workflow_runs (workflow_id);
ALTER TABLE goodle_workflow_runs ALTER COLUMN conversation_id TYPE VARCHAR(255);

CREATE TABLE IF NOT EXISTS goodle_workflow_checkpoints (
    id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    run_id VARCHAR(64) NOT NULL, workflow_id VARCHAR(64), graph_version VARCHAR(64), graph_json TEXT NOT NULL,
    inputs_json TEXT, conversation_id VARCHAR(255),
    status VARCHAR(32) NOT NULL, node_states_json TEXT, variable_pool_json TEXT,
    branch_results_json TEXT, iteration_cursors_json TEXT, pending_nodes_json TEXT,
    resume_node_id VARCHAR(255), interrupt_reason TEXT, checkpoint_version BIGINT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(255), lease_expires_at TIMESTAMP, wait_type VARCHAR(64), correlation_key VARCHAR(255),
    resume_token_hash VARCHAR(128), input_schema_json TEXT, wait_expires_at TIMESTAMP, wake_at TIMESTAMP,
    resumed_by VARCHAR(255), resumed_at TIMESTAMP, created_at TIMESTAMP, updated_at TIMESTAMP,
    PRIMARY KEY (id), UNIQUE (tenant_id, run_id)
);
CREATE INDEX IF NOT EXISTS idx_workflow_checkpoint_lease ON goodle_workflow_checkpoints (status, lease_expires_at);
CREATE INDEX IF NOT EXISTS idx_workflow_checkpoint_correlation ON goodle_workflow_checkpoints (tenant_id, correlation_key, status);
ALTER TABLE goodle_workflow_checkpoints ALTER COLUMN conversation_id TYPE VARCHAR(255);

CREATE TABLE IF NOT EXISTS goodle_workflow_run_nodes (
    id VARCHAR(255) NOT NULL, tenant_id VARCHAR(64) NOT NULL DEFAULT 'default', run_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(255) NOT NULL, node_type VARCHAR(64), status VARCHAR(32) NOT NULL, attempt INTEGER NOT NULL DEFAULT 0,
    selected_handle VARCHAR(255), outputs_json TEXT, error TEXT, error_code VARCHAR(128), retryable BOOLEAN,
    idempotency_key VARCHAR(255),
    execution_mode VARCHAR(32), result_cache_policy VARCHAR(32), resumable BOOLEAN,
    started_at TIMESTAMP, finished_at TIMESTAMP, executor_instance VARCHAR(255),
    token_count BIGINT, cost VARCHAR(64), external_status INTEGER, trace_id VARCHAR(128), span_id VARCHAR(128),
    checkpoint_version BIGINT,
    created_at TIMESTAMP, updated_at TIMESTAMP, PRIMARY KEY (id), UNIQUE (tenant_id, run_id, node_id)
);
CREATE INDEX IF NOT EXISTS idx_workflow_run_node_run ON goodle_workflow_run_nodes (tenant_id, run_id, status);
ALTER TABLE goodle_workflow_run_nodes ADD COLUMN IF NOT EXISTS error_code VARCHAR(128);
ALTER TABLE goodle_workflow_run_nodes ADD COLUMN IF NOT EXISTS retryable BOOLEAN;

CREATE TABLE IF NOT EXISTS goodle_workflow_execution_events (
    id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL DEFAULT 'default', run_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(255), event_type VARCHAR(64) NOT NULL, attempt INTEGER, payload_json TEXT,
    input_summary TEXT, output_summary TEXT, token_count BIGINT, cost VARCHAR(64), external_status INTEGER,
    checkpoint_id VARCHAR(64), trace_id VARCHAR(128), span_id VARCHAR(128), executor_instance VARCHAR(255),
    created_at TIMESTAMP, updated_at TIMESTAMP, PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_workflow_event_run ON goodle_workflow_execution_events (tenant_id, run_id, created_at);

CREATE TABLE IF NOT EXISTS goodle_workflow_resume_signals (
    id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL DEFAULT 'default', run_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(255) NOT NULL, correlation_key VARCHAR(255), payload_hash VARCHAR(128), resumed_by VARCHAR(255),
    created_at TIMESTAMP, updated_at TIMESTAMP, PRIMARY KEY (id), UNIQUE (tenant_id, event_id)
);
CREATE INDEX IF NOT EXISTS idx_workflow_signal_run ON goodle_workflow_resume_signals (tenant_id, run_id);

CREATE TABLE IF NOT EXISTS goodle_human_interactions (
    id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL DEFAULT 'default', workflow_id VARCHAR(64), app_id VARCHAR(64),
    run_id VARCHAR(64) NOT NULL, node_id VARCHAR(255) NOT NULL, conversation_id VARCHAR(255), status VARCHAR(32) NOT NULL,
    title VARCHAR(255), description TEXT, form_mode VARCHAR(32), presentation_mode VARCHAR(32), input_schema_json TEXT,
    access_token_hash VARCHAR(128) NOT NULL, delivery_config_json TEXT, submitted_values_json TEXT, submitted_text TEXT,
    submitted_by VARCHAR(255), short_code VARCHAR(16), channel_provider VARCHAR(64), channel_id VARCHAR(128),
    channel_connection_id VARCHAR(64), channel_target VARCHAR(255), channel_conversation_id VARCHAR(255),
    notification_message_id VARCHAR(255), expires_at TIMESTAMP, submitted_at TIMESTAMP, created_at TIMESTAMP, updated_at TIMESTAMP,
    PRIMARY KEY (id), UNIQUE (tenant_id, run_id, node_id), UNIQUE (access_token_hash)
);
CREATE INDEX IF NOT EXISTS idx_human_interaction_conversation ON goodle_human_interactions (tenant_id, conversation_id, created_at);
ALTER TABLE goodle_human_interactions ALTER COLUMN conversation_id TYPE VARCHAR(255);
ALTER TABLE goodle_human_interactions ADD COLUMN IF NOT EXISTS short_code VARCHAR(16);
ALTER TABLE goodle_human_interactions ADD COLUMN IF NOT EXISTS channel_provider VARCHAR(64);
ALTER TABLE goodle_human_interactions ADD COLUMN IF NOT EXISTS channel_id VARCHAR(128);
ALTER TABLE goodle_human_interactions ADD COLUMN IF NOT EXISTS channel_connection_id VARCHAR(64);
ALTER TABLE goodle_human_interactions ADD COLUMN IF NOT EXISTS channel_target VARCHAR(255);
ALTER TABLE goodle_human_interactions ADD COLUMN IF NOT EXISTS channel_conversation_id VARCHAR(255);
ALTER TABLE goodle_human_interactions ADD COLUMN IF NOT EXISTS notification_message_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_human_interaction_channel ON goodle_human_interactions
    (tenant_id, channel_connection_id, channel_target, status, created_at);

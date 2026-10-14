CREATE TABLE IF NOT EXISTS agent_channel_connection (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    owner_type VARCHAR(32) NOT NULL DEFAULT 'USER',
    owner_id VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    channel_id VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    encrypted_credentials TEXT,
    encrypted_config TEXT,
    desired_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    runtime_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    runtime_account_id VARCHAR(64),
    runtime_node_id VARCHAR(128),
    agent_id VARCHAR(64),
    agent_version_id VARCHAR(64),
    runtime_metadata_json TEXT,
    last_error TEXT,
    last_tested_at TIMESTAMP,
    config_version BIGINT NOT NULL DEFAULT 1,
    reconcile_attempts INT NOT NULL DEFAULT 0,
    next_reconcile_at TIMESTAMP,
    reconcile_lease_until TIMESTAMP,
    reconcile_lease_owner VARCHAR(128),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agent_channel_event (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    connection_id VARCHAR(64),
    owner_id VARCHAR(64),
    provider VARCHAR(64) NOT NULL,
    channel_id VARCHAR(128) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(255),
    sender_id VARCHAR(255),
    reply_target_id VARCHAR(255),
    conversation_id VARCHAR(255),
    reply_to_event_id VARCHAR(64),
    idempotency_key VARCHAR(128),
    platform_message_id VARCHAR(255),
    sender_type VARCHAR(32),
    sender_actor_id VARCHAR(64),
    direction VARCHAR(16) NOT NULL DEFAULT 'INBOUND',
    content TEXT,
    message_type VARCHAR(32) NOT NULL DEFAULT 'TEXT',
    attachments_json TEXT,
    content_json TEXT,
    handled BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    reply_content TEXT,
    duration_ms BIGINT,
    error_message TEXT,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP,
    lease_until TIMESTAMP,
    lease_owner VARCHAR(128),
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    event_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agent_tenant_agent_binding (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    default_agent_id VARCHAR(64) NOT NULL,
    default_agent_version_id VARCHAR(64),
    fallback_agent_id VARCHAR(64),
    fallback_agent_version_id VARCHAR(64),
    routing_policy_version BIGINT NOT NULL DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id)
);

CREATE TABLE IF NOT EXISTS agent_employee_agent_binding (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    agent_version_id VARCHAR(64),
    routing_policy_version BIGINT NOT NULL DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, employee_id)
);

CREATE TABLE IF NOT EXISTS agent_channel_identity (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    channel_id VARCHAR(128) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    external_user_id VARCHAR(255) NOT NULL,
    enterprise_user_id VARCHAR(64) NOT NULL,
    verification_status VARCHAR(32) NOT NULL DEFAULT 'VERIFIED',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, provider, channel_id, account_id, external_user_id)
);

CREATE TABLE IF NOT EXISTS agent_external_identity (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    platform_tenant_id VARCHAR(128) NOT NULL,
    external_user_id VARCHAR(255) NOT NULL,
    enterprise_user_id VARCHAR(64) NOT NULL,
    verification_status VARCHAR(32) NOT NULL DEFAULT 'VERIFIED',
    binding_method VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    bound_at TIMESTAMP,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, platform, platform_tenant_id, external_user_id)
);

CREATE TABLE IF NOT EXISTS agent_channel_audit (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(64),
    actor_name VARCHAR(255),
    principal_type VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(255),
    outcome VARCHAR(32) NOT NULL,
    details_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS agent_channel_conversation (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    connection_id VARCHAR(64) NOT NULL,
    owner_id VARCHAR(64),
    provider VARCHAR(64) NOT NULL,
    channel_id VARCHAR(128) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(255) NOT NULL,
    agent_id VARCHAR(64),
    agent_version_id VARCHAR(64),
    agent_route_reason VARCHAR(255),
    routing_policy_version BIGINT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'BOT_ACTIVE',
    agent_paused BOOLEAN NOT NULL DEFAULT FALSE,
    assignee_id VARCHAR(64),
    assignee_name VARCHAR(255),
    assignment_group VARCHAR(128),
    handoff_requested_at TIMESTAMP,
    claimed_at TIMESTAMP,
    closed_at TIMESTAMP,
    last_message_at TIMESTAMP,
    last_event_id VARCHAR(64),
    last_message_preview VARCHAR(512),
    unread_count INT NOT NULL DEFAULT 0,
    sla_due_at TIMESTAMP,
    sla_reminder_stage INT NOT NULL DEFAULT 0,
    sla_reminded_at TIMESTAMP,
    sla_reminder_lease_until TIMESTAMP,
    sla_reminder_lease_owner VARCHAR(160),
    lock_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, connection_id, conversation_id)
);
CREATE INDEX IF NOT EXISTS idx_channel_conversation_queue
    ON agent_channel_conversation (tenant_id, status, sla_due_at, last_message_at);
CREATE INDEX IF NOT EXISTS idx_channel_conversation_assignee
    ON agent_channel_conversation (tenant_id, assignee_id, status);
CREATE INDEX IF NOT EXISTS idx_channel_audit_tenant_created
    ON agent_channel_audit (tenant_id, created_at);

-- Keeps existing installations compatible when upgrading from the single-runtime schema.
ALTER TABLE agent_channel_connection ADD COLUMN IF NOT EXISTS runtime_node_id VARCHAR(128);
ALTER TABLE agent_channel_connection ADD COLUMN IF NOT EXISTS agent_version_id VARCHAR(64);
ALTER TABLE agent_channel_connection ADD COLUMN IF NOT EXISTS runtime_metadata_json TEXT;
ALTER TABLE agent_tenant_agent_binding ADD COLUMN IF NOT EXISTS default_agent_version_id VARCHAR(64);
ALTER TABLE agent_tenant_agent_binding ADD COLUMN IF NOT EXISTS fallback_agent_version_id VARCHAR(64);
ALTER TABLE agent_tenant_agent_binding ADD COLUMN IF NOT EXISTS routing_policy_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE agent_employee_agent_binding ADD COLUMN IF NOT EXISTS agent_version_id VARCHAR(64);
ALTER TABLE agent_employee_agent_binding ADD COLUMN IF NOT EXISTS routing_policy_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE agent_channel_event ADD COLUMN IF NOT EXISTS reply_to_event_id VARCHAR(64);
ALTER TABLE agent_channel_event ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);
ALTER TABLE agent_channel_event ADD COLUMN IF NOT EXISTS platform_message_id VARCHAR(255);
ALTER TABLE agent_channel_event ADD COLUMN IF NOT EXISTS sender_type VARCHAR(32);
ALTER TABLE agent_channel_event ADD COLUMN IF NOT EXISTS sender_actor_id VARCHAR(64);
ALTER TABLE agent_channel_event ADD COLUMN IF NOT EXISTS attempts INT NOT NULL DEFAULT 0;
ALTER TABLE agent_channel_event ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP;
ALTER TABLE agent_channel_event ADD COLUMN IF NOT EXISTS lease_until TIMESTAMP;
ALTER TABLE agent_channel_event ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(128);
ALTER TABLE agent_channel_event ADD COLUMN IF NOT EXISTS sent_at TIMESTAMP;
ALTER TABLE agent_channel_event ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP;
ALTER TABLE agent_channel_connection ADD COLUMN IF NOT EXISTS reconcile_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE agent_channel_connection ADD COLUMN IF NOT EXISTS next_reconcile_at TIMESTAMP;
ALTER TABLE agent_channel_connection ADD COLUMN IF NOT EXISTS reconcile_lease_until TIMESTAMP;
ALTER TABLE agent_channel_connection ADD COLUMN IF NOT EXISTS reconcile_lease_owner VARCHAR(128);
ALTER TABLE agent_channel_event ADD COLUMN IF NOT EXISTS message_type VARCHAR(32) NOT NULL DEFAULT 'TEXT';
ALTER TABLE agent_channel_event ADD COLUMN IF NOT EXISTS attachments_json TEXT;
ALTER TABLE agent_channel_event ADD COLUMN IF NOT EXISTS content_json TEXT;
ALTER TABLE agent_channel_event ADD COLUMN IF NOT EXISTS runtime_node_id VARCHAR(128);
-- Rows created by the former single-runtime schema belong to that provider's default node.
-- Backfill before creating the node-scoped unique indexes so upgraded accounts keep routing.
UPDATE agent_channel_connection
SET runtime_node_id = provider || '-default'
WHERE runtime_node_id IS NULL OR TRIM(runtime_node_id) = '';
UPDATE agent_channel_event
SET runtime_node_id = provider || '-default'
WHERE runtime_node_id IS NULL OR TRIM(runtime_node_id) = '';
ALTER TABLE agent_channel_connection DROP CONSTRAINT IF EXISTS agent_channel_connection_provider_channel_id_runtime_account_id_key;
ALTER TABLE agent_channel_event DROP CONSTRAINT IF EXISTS agent_channel_event_provider_channel_id_account_id_message_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS ux_channel_connection_runtime_account
    ON agent_channel_connection (provider, runtime_node_id, channel_id, runtime_account_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_channel_event_runtime_message
    ON agent_channel_event (tenant_id, provider, runtime_node_id, channel_id, account_id, message_id);
ALTER TABLE agent_channel_event ADD COLUMN IF NOT EXISTS reply_target_id VARCHAR(255);
ALTER TABLE agent_channel_conversation ADD COLUMN IF NOT EXISTS agent_id VARCHAR(64);
ALTER TABLE agent_channel_conversation ADD COLUMN IF NOT EXISTS agent_version_id VARCHAR(64);
ALTER TABLE agent_channel_conversation ADD COLUMN IF NOT EXISTS agent_route_reason VARCHAR(255);
ALTER TABLE agent_channel_conversation ADD COLUMN IF NOT EXISTS routing_policy_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE agent_channel_conversation ADD COLUMN IF NOT EXISTS sla_reminder_stage INT NOT NULL DEFAULT 0;
ALTER TABLE agent_channel_conversation ADD COLUMN IF NOT EXISTS sla_reminded_at TIMESTAMP;
ALTER TABLE agent_channel_conversation ADD COLUMN IF NOT EXISTS sla_reminder_lease_until TIMESTAMP;
ALTER TABLE agent_channel_conversation ADD COLUMN IF NOT EXISTS sla_reminder_lease_owner VARCHAR(160);
CREATE INDEX IF NOT EXISTS idx_channel_conversation_sla_reminder
    ON agent_channel_conversation (status, sla_due_at, sla_reminder_stage, sla_reminder_lease_until);
CREATE INDEX IF NOT EXISTS idx_channel_event_reply_to ON agent_channel_event (reply_to_event_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_channel_event_tenant_idempotency
    ON agent_channel_event (tenant_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_channel_event_outbox_due
    ON agent_channel_event (direction, status, next_attempt_at, lease_until);
CREATE UNIQUE INDEX IF NOT EXISTS uk_channel_event_platform_message
    ON agent_channel_event (provider, channel_id, account_id, platform_message_id);

-- agentstart-trigger schema (portable across H2 and MySQL/Postgres).
-- Table names aligned with spring-agent-start:
--   goodle_app_triggers          ← was agent_trigger
--   goodle_trigger_invocations   ← was agent_trigger_invocation

CREATE TABLE IF NOT EXISTS goodle_app_triggers (
    id          VARCHAR(64)  NOT NULL,
    tenant_id   VARCHAR(64)  NOT NULL DEFAULT 'default',
    user_id     VARCHAR(64),
    name        VARCHAR(255),
    type        VARCHAR(32),
    enabled     BOOLEAN DEFAULT TRUE,
    target_type VARCHAR(32),
    target_id   VARCHAR(64),
    config_json TEXT,
    next_fire_at TIMESTAMP,
    last_fire_at TIMESTAMP,
    lock_until TIMESTAMP,
    lock_owner VARCHAR(128),
    fire_count BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    PRIMARY KEY (id)
);

ALTER TABLE goodle_app_triggers ADD COLUMN IF NOT EXISTS user_id VARCHAR(64);

CREATE TABLE IF NOT EXISTS goodle_trigger_invocations (
    id           VARCHAR(64) NOT NULL,
    tenant_id    VARCHAR(64) NOT NULL DEFAULT 'default',
    trigger_id   VARCHAR(64) NOT NULL,
    source       VARCHAR(32),
    conversation_id VARCHAR(128),
    status       VARCHAR(32),
    payload_json TEXT,
    run_id       VARCHAR(64),
    outputs_json TEXT,
    error        TEXT,
    replay_of    VARCHAR(64),
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_trigger_type ON goodle_app_triggers (type, enabled);
CREATE INDEX IF NOT EXISTS idx_trigger_due ON goodle_app_triggers (enabled, type, next_fire_at);
CREATE INDEX IF NOT EXISTS idx_invocation_trigger ON goodle_trigger_invocations (trigger_id);

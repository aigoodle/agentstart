-- agent-start-observability schema (portable across H2 and MySQL/Postgres).
-- goodle_llm_calls was agent_llm_call; renamed for cross-project consistency.

CREATE TABLE IF NOT EXISTS goodle_llm_calls (
    id                VARCHAR(64) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL DEFAULT 'default',
    provider          VARCHAR(128),
    model             VARCHAR(255),
    prompt_tokens     INT,
    completion_tokens INT,
    total_tokens      INT,
    cost_micros       BIGINT,
    latency_ms        BIGINT,
    success           BOOLEAN,
    error_type        VARCHAR(255),
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_llm_call_model ON goodle_llm_calls (model);
CREATE INDEX IF NOT EXISTS idx_llm_call_created ON goodle_llm_calls (created_at);

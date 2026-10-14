CREATE TABLE IF NOT EXISTS goodle_memories (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    owner_id VARCHAR(64),
    conversation_id VARCHAR(128),
    tier VARCHAR(32) NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    importance DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    expires_at TIMESTAMP,
    access_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_agent_memories_scope
    ON goodle_memories (tenant_id, owner_id, conversation_id, tier, created_at);
CREATE INDEX IF NOT EXISTS idx_agent_memories_expiry ON goodle_memories (expires_at);

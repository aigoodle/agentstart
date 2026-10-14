CREATE TABLE IF NOT EXISTS goodle_skills (
    id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1024),
    instructions TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    tool_names_json TEXT,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, code)
);
CREATE INDEX IF NOT EXISTS idx_skills_tenant_status ON goodle_skills (tenant_id, status);

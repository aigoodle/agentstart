CREATE TABLE IF NOT EXISTS agent_connector_installation (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    provider VARCHAR(64) NOT NULL,
    connector_id VARCHAR(255) NOT NULL,
    version VARCHAR(64),
    source VARCHAR(32),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    trust_level VARCHAR(32) NOT NULL DEFAULT 'UNTRUSTED',
    manifest_json TEXT,
    config_schema_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, provider, connector_id)
);

CREATE TABLE IF NOT EXISTS agent_connector_connection (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    installation_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    encrypted_credentials TEXT,
    encrypted_config TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DISCONNECTED',
    expires_at TIMESTAMP,
    last_tested_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agent_connector_execution (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    provider VARCHAR(64) NOT NULL,
    connector_id VARCHAR(255) NOT NULL,
    action_id VARCHAR(255) NOT NULL,
    installation_id VARCHAR(64),
    connection_id VARCHAR(64),
    runtime_node_id VARCHAR(128),
    agent_id VARCHAR(64),
    workflow_id VARCHAR(64),
    run_id VARCHAR(64),
    node_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    duration_ms BIGINT,
    attempts INT NOT NULL DEFAULT 1,
    error_code VARCHAR(128),
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

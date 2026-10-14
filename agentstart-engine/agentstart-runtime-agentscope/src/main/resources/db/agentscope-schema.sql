-- RuntimeContext embeds tenant and caller identity into user/session IDs.
CREATE TABLE IF NOT EXISTS agent_scope_state (
    user_id VARCHAR(512) NOT NULL,
    session_id VARCHAR(512) NOT NULL,
    state_key VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    list_value BOOLEAN NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (user_id, session_id, state_key)
);

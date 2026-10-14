-- agentstart-agent schema (portable across H2 and MySQL/Postgres).
-- Table names aligned with spring-agent-start (Dify parity):
--   goodle_apps                    ← was agent_definition (智能体应用) — lean metadata
--   goodle_app_model_configs       ← 1:1 sidecar carrying prompt / model params /
--                              retrieval / agent behaviour (Dify parity)
--   goodle_app_annotations         ← per-app QA overrides
--   goodle_app_annotation_settings ← retrieval config for annotations
--   goodle_conversations           ← chat session metadata under an app
--   goodle_api_tokens              ← per-app API access tokens
--   goodle_app_sites               ← published widget / hosted site config
--   goodle_tags / goodle_tag_bindings     ← tenant-scoped organisational goodle_tags
--
-- Design note: the split between `goodle_apps` and `goodle_app_model_configs` follows Dify —
-- `goodle_apps` is the at-a-glance catalog row (name / icon / mode / publish state),
-- everything about *how the app behaves* (prompt, model overrides, tools,
-- retrieval) lives in the sidecar keyed by app id. Workflow / chatflow goodle_apps
-- carry an empty sidecar because their behaviour lives in the workflow graph.

CREATE TABLE IF NOT EXISTS goodle_apps (
    id                        VARCHAR(64)  NOT NULL,
    tenant_id                 VARCHAR(64)  NOT NULL DEFAULT 'default',
    app_code                  VARCHAR(100),
    visibility                VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    name                      VARCHAR(255) NOT NULL,
    description               VARCHAR(1024),
    icon                      VARCHAR(64),
    icon_background           VARCHAR(32),
    icon_type                 VARCHAR(32),
    use_icon_as_answer_icon   BOOLEAN DEFAULT FALSE,
    mode                      VARCHAR(32) DEFAULT 'agent',
    status                    VARCHAR(32) DEFAULT 'normal',
    is_public                 BOOLEAN DEFAULT FALSE,
    enable_site               BOOLEAN DEFAULT FALSE,
    enable_api                BOOLEAN DEFAULT FALSE,
    api_rpm                   INT DEFAULT 0,
    api_rph                   INT DEFAULT 0,
    published                 BOOLEAN DEFAULT TRUE,
    -- FK to goodle_workflows.id — the persistent DRAFT workflow this app edits (Dify
    -- parity). Populated on create for workflow/chatflow modes; null for
    -- chat/agent/completion.
    workflow_id               VARCHAR(64),
    -- Denormalised model reference kept on the catalog row so the agent-list
    -- card can render "provider · model" without a JOIN. The source of truth
    -- for runtime resolution is goodle_app_model_configs.
    model_name                VARCHAR(128),
    model_provider            VARCHAR(64),
    created_at                TIMESTAMP,
    updated_at                TIMESTAMP,
    PRIMARY KEY (id)
);

-- Upgrade existing installations; CREATE TABLE IF NOT EXISTS does not add new columns.
ALTER TABLE goodle_apps ADD COLUMN IF NOT EXISTS app_code VARCHAR(100);
ALTER TABLE goodle_apps ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';

CREATE INDEX IF NOT EXISTS idx_apps_tenant_mode ON goodle_apps (tenant_id, mode);
CREATE INDEX IF NOT EXISTS idx_apps_published ON goodle_apps (published);
CREATE UNIQUE INDEX IF NOT EXISTS uk_apps_tenant_code ON goodle_apps (tenant_id, app_code);

CREATE TABLE IF NOT EXISTS goodle_agent_versions (
    id                       VARCHAR(64) NOT NULL,
    tenant_id                VARCHAR(64) NOT NULL,
    app_id                   VARCHAR(64) NOT NULL,
    version_number           INT NOT NULL,
    status                   VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    definition_json          TEXT NOT NULL,
    change_summary           VARCHAR(1024),
    published_by             VARCHAR(64),
    published_at             TIMESTAMP NOT NULL,
    rollback_from_version_id VARCHAR(64),
    created_at               TIMESTAMP,
    updated_at               TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, app_id, version_number)
);
CREATE INDEX IF NOT EXISTS idx_agent_versions_current
    ON goodle_agent_versions (tenant_id, app_id, status, version_number);

-- ============================================================================
-- goodle_app_model_configs — 1:1 sidecar with goodle_apps.id (id == app_id). Carries the
-- entire "编排" drawer payload: system prompt, model overrides, agent
-- strategy / tools / delegation / memory, goodle_dataset / retrieval config, user
-- input form and speech / moderation blobs. Vendor-neutral: thinking mode is
-- a normalized 'auto'|'enabled'|'disabled' flag inside `configs`, translated
-- per-vendor by AgentChatOptionsFactory.
-- ============================================================================
CREATE TABLE IF NOT EXISTS goodle_app_model_configs (
    id                                VARCHAR(64) NOT NULL,
    tenant_id                         VARCHAR(64) NOT NULL DEFAULT 'default',
    app_id                            VARCHAR(64) NOT NULL,
    -- Selected model
    model_provider                    VARCHAR(64),
    model_name                        VARCHAR(128),
    runtime_type                     VARCHAR(64) NOT NULL DEFAULT 'NATIVE',
    runtime_ref                      VARCHAR(255),
    model_json                        TEXT,
    configs                           TEXT,
    -- Prompt
    pre_prompt                        TEXT,
    prompt_type                       VARCHAR(32) DEFAULT 'simple',
    chat_prompt_config                TEXT,
    completion_prompt_config          TEXT,
    -- Chat presentation
    opening_statement                 TEXT,
    suggested_questions_json          TEXT,
    suggested_questions_after_answer  TEXT,
    more_like_this                    TEXT,
    user_input_form_json              TEXT,
    -- Agent behaviour
    agent_mode                        TEXT,
    strategy                          VARCHAR(32),
    tool_names_json                   TEXT,
    skill_ids_json                    TEXT,
    approval_tools_json               TEXT,
    delegate_agent_ids_json           TEXT,
    max_iterations                    INT,
    max_model_calls                   INT,
    max_tool_calls                    INT,
    memory_enabled                    BOOLEAN DEFAULT TRUE,
    memory_window                     INT,
    -- Knowledge / RAG
    dataset_ids_json                  TEXT,
    dataset_configs_json              TEXT,
    file_upload_json                  TEXT,
    external_data_tools               TEXT,
    retriever_resource                TEXT,
    dataset_query_variable            VARCHAR(255),
    -- Speech / moderation
    speech_to_text                    TEXT,
    text_to_speech                    TEXT,
    sensitive_word_avoidance          TEXT,
    created_at                        TIMESTAMP,
    updated_at                        TIMESTAMP,
    PRIMARY KEY (id)
);
ALTER TABLE goodle_app_model_configs ADD COLUMN IF NOT EXISTS runtime_type VARCHAR(64) NOT NULL DEFAULT 'NATIVE';
ALTER TABLE goodle_app_model_configs ADD COLUMN IF NOT EXISTS runtime_ref VARCHAR(255);
ALTER TABLE goodle_app_model_configs ADD COLUMN IF NOT EXISTS max_model_calls INT;
ALTER TABLE goodle_app_model_configs ADD COLUMN IF NOT EXISTS max_tool_calls INT;
ALTER TABLE goodle_app_model_configs ADD COLUMN IF NOT EXISTS skill_ids_json TEXT;

CREATE INDEX IF NOT EXISTS idx_app_model_config_app ON goodle_app_model_configs (app_id);

-- User-authored QA overrides surfaced in the "日志与标注" drawer tab. When a
-- chat query hits `question`, `content` is returned verbatim (bypassing the
-- LLM). Ranking + hit-count bump is wired in a follow-up pass.
CREATE TABLE IF NOT EXISTS goodle_app_annotations (
    id         VARCHAR(64) NOT NULL,
    tenant_id  VARCHAR(64) NOT NULL DEFAULT 'default',
    app_id     VARCHAR(64) NOT NULL,
    question   TEXT,
    content    TEXT,
    hit_count  INT DEFAULT 0,
    enabled    BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_annotation_app ON goodle_app_annotations (app_id);

-- Per-app annotation retrieval configuration (score threshold + embedding
-- model). Effectively singleton per app; upsert by app_id.
CREATE TABLE IF NOT EXISTS goodle_app_annotation_settings (
    id                 VARCHAR(64) NOT NULL,
    tenant_id          VARCHAR(64) NOT NULL DEFAULT 'default',
    app_id             VARCHAR(64) NOT NULL,
    score_threshold    FLOAT,
    embedding_model_id VARCHAR(64),
    enabled            BOOLEAN DEFAULT FALSE,
    created_at         TIMESTAMP,
    updated_at         TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_annotation_setting_app ON goodle_app_annotation_settings (app_id);

-- Chat-session metadata; message content lives in agentstart-memory.
-- holds conversation_id — this row carries user-visible metadata.
CREATE TABLE IF NOT EXISTS goodle_conversations (
    id                VARCHAR(64) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL DEFAULT 'default',
    app_id            VARCHAR(64) NOT NULL,
    name              VARCHAR(255),
    summary           TEXT,
    introduction      TEXT,
    from_source       VARCHAR(32),
    from_end_user_id  VARCHAR(64),
    from_account_id   VARCHAR(64),
    status            VARCHAR(32) DEFAULT 'normal',
    pinned            BOOLEAN DEFAULT FALSE,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_conversation_app ON goodle_conversations (app_id);

-- Durable agent execution state. A run remains queryable after the request or
-- JVM that started it has gone away; version is used for optimistic transitions.
CREATE TABLE IF NOT EXISTS goodle_runs (
    id                VARCHAR(64) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL DEFAULT 'default',
    agent_id          VARCHAR(64) NOT NULL,
    conversation_id   VARCHAR(64),
    status            VARCHAR(32) NOT NULL,
    definition_json   TEXT,
    request_json      TEXT,
    response_json     TEXT,
    error             TEXT,
    version           BIGINT NOT NULL DEFAULT 0,
    event_sequence    BIGINT NOT NULL DEFAULT 0,
    started_at        TIMESTAMP,
    finished_at       TIMESTAMP,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_agent_run_agent_created ON goodle_runs (agent_id, created_at);
CREATE INDEX IF NOT EXISTS idx_agent_run_conversation ON goodle_runs (conversation_id);
CREATE INDEX IF NOT EXISTS idx_agent_run_status ON goodle_runs (status);

-- Append-only projection source consumed by SSE clients, observability adapters
-- and future checkpoint/replay support. sequence_no is monotonic within a run.
CREATE TABLE IF NOT EXISTS goodle_run_events (
    id            VARCHAR(64) NOT NULL,
    tenant_id     VARCHAR(64) NOT NULL DEFAULT 'default',
    run_id        VARCHAR(64) NOT NULL,
    sequence_no   BIGINT NOT NULL,
    event_type    VARCHAR(64) NOT NULL,
    payload_json  TEXT,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (run_id, sequence_no)
);
CREATE INDEX IF NOT EXISTS idx_agent_run_event_stream ON goodle_run_events (run_id, sequence_no);

-- Per-app API access tokens. Value is generated server-side on create.
CREATE TABLE IF NOT EXISTS goodle_api_tokens (
    id            VARCHAR(64) NOT NULL,
    tenant_id     VARCHAR(64) NOT NULL DEFAULT 'default',
    app_id        VARCHAR(64) NOT NULL,
    type          VARCHAR(32) DEFAULT 'app',
    name          VARCHAR(255),
    token         VARCHAR(128) NOT NULL,
    last_used_at  TIMESTAMP,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_api_token_app ON goodle_api_tokens (app_id);
CREATE INDEX IF NOT EXISTS idx_api_token_value ON goodle_api_tokens (token);

-- Published widget / hosted-site config for an app. Singleton per app.
CREATE TABLE IF NOT EXISTS goodle_app_sites (
    id                        VARCHAR(64) NOT NULL,
    tenant_id                 VARCHAR(64) NOT NULL DEFAULT 'default',
    app_id                    VARCHAR(64) NOT NULL,
    title                     VARCHAR(255),
    icon                      VARCHAR(64),
    icon_background           VARCHAR(32),
    icon_type                 VARCHAR(32),
    description               TEXT,
    default_language          VARCHAR(16),
    copyright                 VARCHAR(255),
    privacy_policy            TEXT,
    custom_disclaimer         TEXT,
    code                      VARCHAR(32),
    chat_color_theme          TEXT,
    chat_color_theme_inverted BOOLEAN DEFAULT FALSE,
    show_workflow_steps       BOOLEAN DEFAULT FALSE,
    use_icon_as_answer_icon   BOOLEAN DEFAULT FALSE,
    status                    VARCHAR(32) DEFAULT 'normal',
    created_at                TIMESTAMP,
    updated_at                TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_app_site_app ON goodle_app_sites (app_id);
CREATE INDEX IF NOT EXISTS idx_app_site_code ON goodle_app_sites (code);

-- Tenant-scoped organisational goodle_tags applied to goodle_apps or datasets.
CREATE TABLE IF NOT EXISTS goodle_tags (
    id         VARCHAR(64) NOT NULL,
    tenant_id  VARCHAR(64) NOT NULL DEFAULT 'default',
    type       VARCHAR(32) DEFAULT 'app',
    name       VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_tag_tenant_type ON goodle_tags (tenant_id, type);

CREATE TABLE IF NOT EXISTS goodle_tag_bindings (
    id           VARCHAR(64) NOT NULL,
    tenant_id    VARCHAR(64) NOT NULL DEFAULT 'default',
    tag_id       VARCHAR(64) NOT NULL,
    target_id    VARCHAR(64) NOT NULL,
    target_type  VARCHAR(32) DEFAULT 'app',
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_tag_binding_target ON goodle_tag_bindings (target_id, target_type);
CREATE INDEX IF NOT EXISTS idx_tag_binding_tag ON goodle_tag_bindings (tag_id);

-- Application data grants reference host-owned user/role/department IDs.
ALTER TABLE goodle_apps ADD COLUMN IF NOT EXISTS data_access_mode VARCHAR(20) NOT NULL DEFAULT 'ALL';
CREATE TABLE IF NOT EXISTS goodle_app_permissions (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    app_id VARCHAR(64) NOT NULL,
    subject_type VARCHAR(20) NOT NULL CHECK (subject_type IN ('USER', 'ROLE', 'DEPARTMENT')),
    subject_id VARCHAR(128) NOT NULL,
    include_descendants BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_app_permission_subject UNIQUE (tenant_id, app_id, subject_type, subject_id),
    CONSTRAINT ck_app_permission_descendants CHECK (subject_type = 'DEPARTMENT' OR include_descendants = FALSE)
);
CREATE INDEX IF NOT EXISTS idx_app_permission_subject ON goodle_app_permissions (tenant_id, subject_type, subject_id);

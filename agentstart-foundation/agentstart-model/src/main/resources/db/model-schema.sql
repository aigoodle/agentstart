-- agent-start-model schema (portable across H2 and MySQL).
-- Dify-parity 6-table model layer:
--   goodle_model_provider          -- provider definitions (DB-driven, seeded from Java built-ins)
--   goodle_predefined_model        -- provider catalog (DB-driven, seeded)
--   goodle_provider_credential     -- tenant's saved credentials
--   goodle_model                   -- tenant's CUSTOM-registered models (with overrides)
--   goodle_provider_model_setting  -- per-model enable/disable per tenant (missing row = enabled, Dify semantics)
--   goodle_tenant_default_model    -- tenant default per model_type

-- Provider definitions. Rows with source='builtin' are seeded from Java ModelProvider
-- beans at startup — providing the Maven-loaded default catalog. Rows with
-- source='external'|'custom' can be added by other modules (via seeder callbacks
-- or admin UI) without touching Java code — supporting the "extend without redeploy"
-- flow. tenant_id='system' = global; tenant_id=<xxx> = tenant-private definition.
CREATE TABLE IF NOT EXISTS goodle_model_provider (
    id                             VARCHAR(64)  NOT NULL,
    tenant_id                      VARCHAR(64)  NOT NULL DEFAULT 'system',
    name                           VARCHAR(255) NOT NULL,   -- e.g. 'openai', 'langgenius/tongyi/tongyi'
    label                          VARCHAR(255) NOT NULL,
    description                    VARCHAR(1024),
    icon                           VARCHAR(255),
    svg_icon                       TEXT,                    -- raw SVG markup for user-defined providers
    supported_model_types          TEXT         NOT NULL,   -- JSON array
    credential_schema              TEXT         NOT NULL,   -- JSON array of CredentialField
    default_parameter_rules        TEXT,                    -- JSON map<ModelType, ModelParameterRule[]>
    implementation_key             VARCHAR(128) NOT NULL,   -- lookup into ModelProviderRegistry (Java bean key)
    default_base_url               VARCHAR(1024),
    source                         VARCHAR(32)  NOT NULL DEFAULT 'builtin',  -- 'builtin' | 'external' | 'custom'
    sort_order                     INT          NOT NULL DEFAULT 0,
    enabled                        BOOLEAN      NOT NULL DEFAULT TRUE,
    supports_remote_model_listing  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                     TIMESTAMP,
    updated_at                     TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_model_provider_tenant_name ON goodle_model_provider (tenant_id, name);

-- Predefined model catalog — shipped by the provider (via manifest / Java seed /
-- external module). Read-only "what's available"; tenant selections are elsewhere.
CREATE TABLE IF NOT EXISTS goodle_predefined_model (
    id                VARCHAR(64)  NOT NULL,
    tenant_id         VARCHAR(64)  NOT NULL DEFAULT 'system',  -- 'system' = global; a tenant may add its own predefined entries
    provider_name     VARCHAR(255) NOT NULL,   -- FK to goodle_model_provider.name
    model             VARCHAR(255) NOT NULL,   -- e.g. 'gpt-4o'
    label             VARCHAR(255) NOT NULL,
    model_type        VARCHAR(32)  NOT NULL,
    features          TEXT,                    -- JSON array of ModelFeature names
    context_length    INT,
    dimensions        INT,
    parameter_rules   TEXT,                    -- JSON array of ModelParameterRule (override provider default)
    sort_order        INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_predef_provider_type ON goodle_predefined_model (provider_name, model_type);
CREATE INDEX IF NOT EXISTS idx_predef_tenant_provider ON goodle_predefined_model (tenant_id, provider_name);

CREATE TABLE IF NOT EXISTS goodle_provider_credential (
    id               VARCHAR(64)  NOT NULL,
    tenant_id        VARCHAR(64)  NOT NULL DEFAULT 'default',
    provider_name    VARCHAR(128) NOT NULL,
    credential_name  VARCHAR(128),
    encrypted_config TEXT,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    PRIMARY KEY (id)
);

-- Tenant-registered CUSTOM models — only when the user has model-specific
-- overrides (dedicated apiKey, self-hosted baseUrl, Volcengine endpointId,
-- non-standard dimensions). Predefined models discovered from vendor listing
-- are NOT saved here (Dify-parity: refresh is display-only).
CREATE TABLE IF NOT EXISTS goodle_model (
    id               VARCHAR(64)  NOT NULL,
    tenant_id        VARCHAR(64)  NOT NULL DEFAULT 'default',
    provider_name    VARCHAR(128) NOT NULL,
    model_name       VARCHAR(255) NOT NULL,
    model_type       VARCHAR(32)  NOT NULL,
    credential_id    VARCHAR(64),
    encrypted_config TEXT,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,  -- DEPRECATED: enable/disable moved to goodle_provider_model_setting
    is_default       BOOLEAN      NOT NULL DEFAULT FALSE, -- DEPRECATED: defaults moved to goodle_tenant_default_model
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    PRIMARY KEY (id)
);

-- Enable/disable per (tenant, provider, model, model_type). Missing row means
-- ENABLED (Dify convention) — so a freshly-configured provider gets every
-- predefined model enabled without needing to insert 100 rows.
CREATE TABLE IF NOT EXISTS goodle_provider_model_setting (
    id                     VARCHAR(64)  NOT NULL,
    tenant_id              VARCHAR(64)  NOT NULL DEFAULT 'default',
    provider_name          VARCHAR(128) NOT NULL,
    model_name             VARCHAR(255) NOT NULL,
    model_type             VARCHAR(32)  NOT NULL,
    enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
    load_balancing_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMP,
    updated_at             TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_setting_lookup
    ON goodle_provider_model_setting (tenant_id, provider_name, model_name, model_type);

-- Tenant default per model_type (Dify-parity tenant_default_models).
CREATE TABLE IF NOT EXISTS goodle_tenant_default_model (
    id             VARCHAR(64)  NOT NULL,
    tenant_id      VARCHAR(64)  NOT NULL DEFAULT 'default',
    provider_name  VARCHAR(128) NOT NULL,
    model_name     VARCHAR(255) NOT NULL,
    model_type     VARCHAR(32)  NOT NULL,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_default_tenant_type ON goodle_tenant_default_model (tenant_id, model_type);

CREATE INDEX IF NOT EXISTS idx_agent_model_tenant_type ON goodle_model (tenant_id, model_type);
CREATE INDEX IF NOT EXISTS idx_agent_cred_tenant_provider ON goodle_provider_credential (tenant_id, provider_name);

-- Reusable prompt templates: text with {{#var#}} placeholders that any agent or
-- workflow LLM node can reference. Dify's "prompt template" concept.
CREATE TABLE IF NOT EXISTS goodle_prompt_template (
    id           VARCHAR(64)  NOT NULL,
    tenant_id    VARCHAR(64)  NOT NULL DEFAULT 'default',
    name         VARCHAR(255) NOT NULL,
    category     VARCHAR(64),
    description  VARCHAR(1024),
    content      TEXT         NOT NULL,
    tags_json    TEXT,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_prompt_tenant_category ON goodle_prompt_template (tenant_id, category);

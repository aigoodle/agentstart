-- agentstart-knowledge schema (portable across H2 and MySQL).
-- Table names aligned with the reference spring-agent-start project (Dify-parity):
--   goodle_dataset               ← was agent_dataset
--   goodle_documents             ← was agent_knowledge_document
--   goodle_document_segments     ← was agent_segment
--   goodle_embeddings            ← was agent_vector (JDBC vector-store fallback)
--   goodle_dataset_query         ← was agent_dataset_hit_test_log

CREATE TABLE IF NOT EXISTS goodle_dataset (
    id                    VARCHAR(64)  NOT NULL,
    tenant_id             VARCHAR(64)  NOT NULL DEFAULT 'default',
    name                  VARCHAR(255) NOT NULL,
    description           TEXT,
    embedding_model_id    VARCHAR(64),
    indexing_technique    VARCHAR(32),
    process_rule_json     TEXT,
    retrieval_config_json TEXT,
    vector_store          VARCHAR(64),
    active_index_version_id VARCHAR(64),
    document_count        INT DEFAULT 0,
    segment_count         INT DEFAULT 0,
    created_at            TIMESTAMP,
    updated_at            TIMESTAMP,
    PRIMARY KEY (id)
);
ALTER TABLE goodle_dataset ADD COLUMN IF NOT EXISTS active_index_version_id VARCHAR(64);

-- Compatibility migration: ECONOMY depended on the removed in-JVM keyword index.
-- Existing datasets remain valid and now use the supported vector indexing path.
UPDATE goodle_dataset
SET indexing_technique = 'HIGH_QUALITY'
WHERE indexing_technique = 'ECONOMY';

CREATE TABLE IF NOT EXISTS goodle_index_versions (
    id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    dataset_id VARCHAR(64) NOT NULL,
    version VARCHAR(128) NOT NULL,
    embedding_model_version VARCHAR(255),
    chunking_rule_version VARCHAR(255),
    content_checksum VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    document_count INT DEFAULT 0,
    segment_count INT DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_index_version_dataset_version
    ON goodle_index_versions (tenant_id, dataset_id, version);
CREATE INDEX IF NOT EXISTS idx_index_version_status
    ON goodle_index_versions (tenant_id, dataset_id, status);

CREATE TABLE IF NOT EXISTS goodle_documents (
    id            VARCHAR(64)  NOT NULL,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    dataset_id    VARCHAR(64)  NOT NULL,
    name          VARCHAR(512),
    source_type   VARCHAR(32),
    status        VARCHAR(32),
    error_message TEXT,
    word_count    INT,
    segment_count INT,
    parser_name   VARCHAR(64),
    media_type    VARCHAR(255),
    page_count    INT,
    block_count   INT,
    parse_warnings_json TEXT,
    parsed_document_json TEXT,
    source_data_base64 TEXT,
    file_size BIGINT,
    source_checksum VARCHAR(64),
    enabled       BOOLEAN DEFAULT TRUE,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    PRIMARY KEY (id)
);
ALTER TABLE goodle_documents ADD COLUMN IF NOT EXISTS parser_name VARCHAR(64);
ALTER TABLE goodle_documents ADD COLUMN IF NOT EXISTS media_type VARCHAR(255);
ALTER TABLE goodle_documents ADD COLUMN IF NOT EXISTS page_count INT;
ALTER TABLE goodle_documents ADD COLUMN IF NOT EXISTS block_count INT;
ALTER TABLE goodle_documents ADD COLUMN IF NOT EXISTS parse_warnings_json TEXT;
ALTER TABLE goodle_documents ADD COLUMN IF NOT EXISTS parsed_document_json TEXT;
ALTER TABLE goodle_documents ADD COLUMN IF NOT EXISTS source_data_base64 TEXT;
ALTER TABLE goodle_documents ADD COLUMN IF NOT EXISTS file_size BIGINT;
ALTER TABLE goodle_documents ADD COLUMN IF NOT EXISTS source_checksum VARCHAR(64);

-- Sidecar table for the async ingestion queue. Presence of a row means the
-- corresponding document is still "in flight" (PARSING / PENDING / CHUNKING /
-- INDEXING); the async runner deletes the row on COMPLETED. Keeping raw_text
-- here instead of on `goodle_documents` keeps list queries lightweight — no
-- multi-MB text blobs coming back on a `SELECT * FROM goodle_documents`.
CREATE TABLE IF NOT EXISTS goodle_document_ingest_queue (
    document_id VARCHAR(64) NOT NULL,
    dataset_id  VARCHAR(64) NOT NULL,
    tenant_id   VARCHAR(64) NOT NULL DEFAULT 'default',
    filename    VARCHAR(512),
    source_type VARCHAR(32),
    raw_text    TEXT,
    parsed_document_json TEXT,
    retry_count INT DEFAULT 0,
    idempotency_key VARCHAR(255),
    status VARCHAR(32) DEFAULT 'READY',
    claimed_by VARCHAR(128),
    lease_expires_at TIMESTAMP,
    next_attempt_at TIMESTAMP,
    last_error TEXT,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    PRIMARY KEY (document_id)
);
CREATE INDEX IF NOT EXISTS idx_ingest_queue_dataset ON goodle_document_ingest_queue (dataset_id);
ALTER TABLE goodle_document_ingest_queue ADD COLUMN IF NOT EXISTS parsed_document_json TEXT;
ALTER TABLE goodle_document_ingest_queue ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255);
ALTER TABLE goodle_document_ingest_queue ADD COLUMN IF NOT EXISTS status VARCHAR(32) DEFAULT 'READY';
ALTER TABLE goodle_document_ingest_queue ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(128);
ALTER TABLE goodle_document_ingest_queue ADD COLUMN IF NOT EXISTS lease_expires_at TIMESTAMP;
ALTER TABLE goodle_document_ingest_queue ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP;
ALTER TABLE goodle_document_ingest_queue ADD COLUMN IF NOT EXISTS last_error TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ingest_idempotency ON goodle_document_ingest_queue (idempotency_key);
CREATE INDEX IF NOT EXISTS idx_ingest_claim ON goodle_document_ingest_queue (status, next_attempt_at, lease_expires_at);

CREATE TABLE IF NOT EXISTS goodle_document_segments (
    id            VARCHAR(64)  NOT NULL,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    dataset_id    VARCHAR(64)  NOT NULL,
    document_id   VARCHAR(64)  NOT NULL,
    position      INT,
    content       TEXT,
    token_count   INT,
    metadata_json TEXT,
    parent_id     VARCHAR(64),
    vector_id     VARCHAR(64),
    index_version_id VARCHAR(64),
    enabled       BOOLEAN DEFAULT TRUE,
    hash          VARCHAR(32),
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    PRIMARY KEY (id)
);
ALTER TABLE goodle_document_segments ADD COLUMN IF NOT EXISTS index_version_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_segment_index_version ON goodle_document_segments (index_version_id);

-- Optional table for the built-in JDBC vector store (spring-agent.knowledge.vector-store=jdbc).
CREATE TABLE IF NOT EXISTS goodle_embeddings (
    id            VARCHAR(64) NOT NULL,
    dataset_id    VARCHAR(64) NOT NULL,
    content       TEXT,
    metadata_json TEXT,
    embedding     TEXT,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_embeddings_dataset ON goodle_embeddings (dataset_id);

CREATE INDEX IF NOT EXISTS idx_doc_dataset ON goodle_documents (dataset_id);
CREATE INDEX IF NOT EXISTS idx_segment_dataset ON goodle_document_segments (dataset_id);
CREATE INDEX IF NOT EXISTS idx_segment_document ON goodle_document_segments (document_id);

-- Retrieval query log: every dry-run + production retrieval recorded for later
-- comparison / debugging. Powers the "recent queries" panel of the goodle_dataset
-- detail page so users can eyeball retrieval quality drift.
CREATE TABLE IF NOT EXISTS goodle_dataset_query (
    id           VARCHAR(64) NOT NULL,
    tenant_id    VARCHAR(64) NOT NULL DEFAULT 'default',
    dataset_id   VARCHAR(64) NOT NULL,
    query        TEXT,
    method       VARCHAR(32),
    top_k        INT,
    results_json TEXT,
    hit_count    INT,
    latency_ms   INT,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_dataset_query_dataset ON goodle_dataset_query (dataset_id);

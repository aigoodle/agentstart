package io.aigoodle.knowledge.enums;

/**
 * Lifecycle of a knowledge document as it moves through the ingestion pipeline.
 */
public enum DocumentStatus {
    PENDING,
    PARSING,
    CHUNKING,
    INDEXING,
    COMPLETED,
    FAILED,
    DISABLED
}

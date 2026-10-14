package io.aigoodle.knowledge.async;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.knowledge.enums.IngestionJobStatus;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Sidecar row that carries the "large payload" of an async ingestion task —
 * the already-extracted plain text plus enough context to run the rest of the
 * pipeline (chunk → index).
 *
 * <p>Kept separate from {@code documents} so the main list query stays
 * lightweight — you don't want {@code SELECT * FROM goodle_documents} pulling
 * multi-MB {@code raw_text} blobs for a card grid. The row is
 * <b>deleted</b> once the worker successfully processes the task, so this
 * table only ever holds "in-flight" work.</p>
 */
@Data
@TableName("goodle_document_ingest_queue")
public class DocumentIngestQueueEntity {

    /**
     * FK / PK — same value as {@code documents.id}. Marked {@link IdType#INPUT}
     * because the caller (KnowledgeService.enqueueIngest) already knows the
     * document id — MyBatis-Plus must NOT mint a fresh UUID here.
     */
    @TableId(value = "document_id", type = IdType.INPUT)
    private String documentId;

    private String datasetId;

    private String tenantId;

    private String filename;

    /** e.g. {@code text}, {@code markdown}, {@code file}. */
    private String sourceType;

    /**
     * Already-extracted plain text. Extract runs synchronously in the upload
     * handler because it's memory-bounded by the incoming request anyway;
     * chunking + embedding go async because they can spawn dozens of LLM
     * calls per document.
     */
    private String rawText;

    /** JSON serialized ParsedDocument, preserving blocks/pages/tables for async chunking. */
    private String parsedDocumentJson;

    /** Number of times a worker tried and failed. 0 = fresh. */
    private Integer retryCount;

    /** Stable submission identity; protected by a database unique index. */
    private String idempotencyKey;

    private IngestionJobStatus status;

    private String claimedBy;

    private LocalDateTime leaseExpiresAt;

    private LocalDateTime nextAttemptAt;

    private String lastError;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

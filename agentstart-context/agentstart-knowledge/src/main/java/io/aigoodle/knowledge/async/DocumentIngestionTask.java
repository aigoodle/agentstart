package io.aigoodle.knowledge.async;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message payload published to the ingest queue. Deliberately minimal — every
 * async worker carries the trusted tenant boundary plus the document primary key, then it re-hydrates the
 * dataset context + raw text from the DB (via {@link DocumentIngestQueueEntity}).
 * Keeping the message small means huge documents don't bloat the queue.
 *
 * <p>Retry counter is threaded through so consumers can implement backoff or
 * dead-letter after N retries without the DB touching the message body.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentIngestionTask implements Serializable {

    /** Tenant captured before leaving the authenticated request thread. */
    private String tenantId;

    /** FK to {@code documents.id}. */
    private String documentId;

    /**
     * How many times this task has been redelivered. Populated by
     * {@code DocumentIngestionQueue} on republish. Zero on first delivery.
     */
    private int retryCount;
}

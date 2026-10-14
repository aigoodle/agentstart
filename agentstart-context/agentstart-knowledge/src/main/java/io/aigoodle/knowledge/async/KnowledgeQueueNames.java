package io.aigoodle.knowledge.async;

/**
 * Single source of truth for the AMQP topology names. Kept in a POJO instead
 * of {@code @Value} injections so the publisher, consumer and topology
 * auto-config can't drift.
 *
 * <p>Layout:</p>
 * <pre>
 *   [ upload API ] → EXCHANGE ─(ROUTING_KEY)→ QUEUE ─→ [ worker ]
 *                                                │
 *                                                │ 3 retries, then
 *                                                ▼
 *                                             DLQ_QUEUE
 * </pre>
 *
 * <p>The DLQ is bound to a dedicated {@code DLQ_EXCHANGE} so ops can
 * subscribe to it separately (monitoring / re-queue tools) without touching
 * the main topology.</p>
 */
public final class KnowledgeQueueNames {

    private KnowledgeQueueNames() {}

    public static final String EXCHANGE = "kb.document";
    public static final String QUEUE = "kb.document.ingest";
    public static final String ROUTING_KEY = "ingest";

    public static final String DLQ_EXCHANGE = "kb.document.dlq";
    public static final String DLQ_QUEUE = "kb.document.ingest.dlq";
    public static final String DLQ_ROUTING_KEY = "ingest.dlq";
}

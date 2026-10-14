package io.aigoodle.knowledge.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * AMQP listener that drains the ingest queue and hands each task to the
 * shared {@link DocumentIngestionRunner}. When the runner reports failure the
 * listener throws {@link AmqpRejectAndDontRequeueException} so the broker
 * routes the message to the DLQ (retries are broker-side via dead-letter TTL
 * — see {@code GoodleKnowledgeAutoConfiguration}). Successful runs are
 * ack'd automatically.
 *
 * <p>Concurrency, prefetch and retry counts are all set on the
 * {@link org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory}
 * so this class stays as a straightforward transform. Wiring both here means
 * changing broker knobs doesn't force a listener change.</p>
 */
public class DocumentIngestionListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionListener.class);

    private final DocumentIngestionRunner runner;

    public DocumentIngestionListener(DocumentIngestionRunner runner) {
        this.runner = runner;
    }

    @RabbitListener(queues = KnowledgeQueueNames.QUEUE)
    public void onMessage(DocumentIngestionTask task) {
        if (task == null || task.getTenantId() == null || task.getDocumentId() == null) {
            log.warn("Received malformed ingest task; dropping");
            return;
        }
        boolean ok;
        try {
            ok = runner.run(task.getTenantId(), task.getDocumentId());
        } catch (Throwable t) {
            log.error("Unexpected error running ingestion task {}", task.getDocumentId(), t);
            ok = false;
        }
        if (!ok) {
            // Throwing this specific exception tells Spring AMQP to NOT
            // requeue; broker routes to DLQ via the queue's x-dead-letter
            // arguments. The document row is already marked FAILED by the
            // runner so users see a clear status.
            throw new AmqpRejectAndDontRequeueException("ingestion failed for document " + task.getDocumentId());
        }
    }
}

package io.aigoodle.knowledge.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Publishes {@link DocumentIngestionTask}s onto a durable AMQP queue so a
 * (possibly separate) worker JVM can process the extract/index pipeline.
 *
 * <p>Serialisation: JSON via Spring AMQP's Jackson2 message converter —
 * configured on the {@link RabbitTemplate} by the auto-config, so this class
 * just publishes plain POJOs.</p>
 *
 * <p>Exchange / routing keys come from constants on
 * {@link KnowledgeQueueNames} so publisher and consumer can't drift.</p>
 */
public class RabbitDocumentIngestionQueue implements DocumentIngestionQueue {

    private static final Logger log = LoggerFactory.getLogger(RabbitDocumentIngestionQueue.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitDocumentIngestionQueue(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void enqueue(DocumentIngestionTask task) {
        if (task == null || task.getDocumentId() == null) return;
        rabbitTemplate.convertAndSend(KnowledgeQueueNames.EXCHANGE, KnowledgeQueueNames.ROUTING_KEY, task);
        log.debug("Enqueued ingestion task for document {}", task.getDocumentId());
    }
}

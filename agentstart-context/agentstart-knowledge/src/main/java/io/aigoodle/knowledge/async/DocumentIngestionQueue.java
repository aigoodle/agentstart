package io.aigoodle.knowledge.async;

/**
 * SPI over the async ingestion channel. Two shipped implementations:
 * <ul>
 *   <li>{@code SyncDocumentIngestionQueue} — inline fallback that calls the
 *       runner immediately. Default when RabbitMQ isn't configured.</li>
 *   <li>{@code RabbitDocumentIngestionQueue} — publishes to a durable AMQP
 *       queue so workers can process independently.</li>
 * </ul>
 *
 * <p>The upload handler just calls {@link #enqueue(DocumentIngestionTask)} and
 * returns; the difference between "small dev deployment" and "prod with
 * multiple workers" is a single bean swap.</p>
 */
public interface DocumentIngestionQueue {

    /**
     * Publish a task. Contract: this method must NOT block on the actual
     * processing — hosts wire in a queue precisely so the HTTP thread frees
     * up immediately. Sync implementations run the work on a background
     * thread pool to keep that contract.
     */
    void enqueue(DocumentIngestionTask task);
}

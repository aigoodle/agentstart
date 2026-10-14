package io.aigoodle.knowledge.async;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback {@link DocumentIngestionQueue} implementation used when RabbitMQ
 * is not configured. Runs the runner on a background thread pool so the HTTP
 * upload handler still returns immediately — same contract as the AMQP impl,
 * just without a durable queue behind it.
 *
 * <p>Note: on JVM crash, in-flight tasks are lost. The sidecar
 * {@code document_ingest_queue} row survives, so a health-check /
 * bootstrap-scan job can requeue them on restart. Consumers who need
 * at-least-once semantics with durability should switch to the AMQP impl.</p>
 */
public class SyncDocumentIngestionQueue implements DocumentIngestionQueue {

    private static final Logger log = LoggerFactory.getLogger(SyncDocumentIngestionQueue.class);

    private final DocumentIngestionRunner runner;
    private final ExecutorService executor;

    public SyncDocumentIngestionQueue(DocumentIngestionRunner runner, int workerThreads) {
        this.runner = runner;
        this.executor = Executors.newFixedThreadPool(Math.max(1, workerThreads), new NamedThreadFactory());
    }

    @Override
    public void enqueue(DocumentIngestionTask task) {
        if (task == null || task.getDocumentId() == null) return;
        executor.submit(() -> {
            try {
                runner.run(task.getTenantId(), task.getDocumentId());
            } catch (Throwable t) {
                // The runner already logs + persists FAILED status; this is
                // last-ditch defence against runaway RuntimeExceptions that
                // could otherwise poison the executor.
                log.error("Uncaught error running ingestion task {}", task.getDocumentId(), t);
            }
        });
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "kb-ingest-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}

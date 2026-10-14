package io.aigoodle.knowledge.async;

/** Operator-facing replay API for isolated poison documents. */
public class IngestionRecoveryService {
    private final IngestionJobCoordinator coordinator;
    private final DocumentIngestionQueue queue;

    public IngestionRecoveryService(IngestionJobCoordinator coordinator, DocumentIngestionQueue queue) {
        this.coordinator = coordinator;
        this.queue = queue;
    }

    public boolean replay(String tenantId, String documentId) {
        if (!coordinator.replay(tenantId, documentId)) return false;
        queue.enqueue(new DocumentIngestionTask(tenantId, documentId, 0));
        return true;
    }
}

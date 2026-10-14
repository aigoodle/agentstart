package io.aigoodle.knowledge.async;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.knowledge.enums.IngestionJobStatus;
import io.aigoodle.knowledge.mapper.DocumentIngestQueueMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** Database-backed lease, retry/backoff and poison-document coordinator. */
public class IngestionJobCoordinator {
    private final DocumentIngestQueueMapper mapper;
    private final Duration leaseDuration;
    private final Duration baseBackoff;
    private final int maxAttempts;

    public IngestionJobCoordinator(
            DocumentIngestQueueMapper mapper, Duration leaseDuration, Duration baseBackoff, int maxAttempts) {
        this.mapper = mapper;
        this.leaseDuration = leaseDuration;
        this.baseBackoff = baseBackoff;
        this.maxAttempts = maxAttempts;
    }

    public boolean claim(String tenantId, String documentId, String workerId) {
        LocalDateTime now = LocalDateTime.now();
        return mapper.update(
                        null,
                        new LambdaUpdateWrapper<DocumentIngestQueueEntity>()
                                .set(DocumentIngestQueueEntity::getStatus, IngestionJobStatus.CLAIMED)
                                .set(DocumentIngestQueueEntity::getClaimedBy, workerId)
                                .set(DocumentIngestQueueEntity::getLeaseExpiresAt, now.plus(leaseDuration))
                                .eq(DocumentIngestQueueEntity::getTenantId, tenantId)
                                .eq(DocumentIngestQueueEntity::getDocumentId, documentId)
                                .and(w -> w.in(
                                                DocumentIngestQueueEntity::getStatus,
                                                IngestionJobStatus.READY,
                                                IngestionJobStatus.RETRY_WAIT)
                                        .or()
                                        .lt(DocumentIngestQueueEntity::getLeaseExpiresAt, now))
                                .and(w -> w.isNull(DocumentIngestQueueEntity::getNextAttemptAt)
                                        .or()
                                        .le(DocumentIngestQueueEntity::getNextAttemptAt, now)))
                == 1;
    }

    public IngestionJobStatus recordFailure(String tenantId, String documentId, String error) {
        DocumentIngestQueueEntity job = find(tenantId, documentId);
        if (job == null) return IngestionJobStatus.POISONED;
        int attempts = (job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1;
        IngestionJobStatus status =
                attempts >= maxAttempts ? IngestionJobStatus.POISONED : IngestionJobStatus.RETRY_WAIT;
        long multiplier = 1L << Math.min(20, Math.max(0, attempts - 1));
        mapper.update(
                null,
                new LambdaUpdateWrapper<DocumentIngestQueueEntity>()
                        .set(DocumentIngestQueueEntity::getRetryCount, attempts)
                        .set(DocumentIngestQueueEntity::getStatus, status)
                        .set(DocumentIngestQueueEntity::getLastError, error)
                        .set(DocumentIngestQueueEntity::getClaimedBy, null)
                        .set(DocumentIngestQueueEntity::getLeaseExpiresAt, null)
                        .set(
                                DocumentIngestQueueEntity::getNextAttemptAt,
                                status == IngestionJobStatus.POISONED
                                        ? null
                                        : LocalDateTime.now().plus(baseBackoff.multipliedBy(multiplier)))
                        .eq(DocumentIngestQueueEntity::getTenantId, tenantId)
                        .eq(DocumentIngestQueueEntity::getDocumentId, documentId));
        return status;
    }

    /** Explicit operator action; poison jobs are never replayed implicitly. */
    public boolean replay(String tenantId, String documentId) {
        return mapper.update(
                        null,
                        new LambdaUpdateWrapper<DocumentIngestQueueEntity>()
                                .set(DocumentIngestQueueEntity::getStatus, IngestionJobStatus.READY)
                                .set(DocumentIngestQueueEntity::getRetryCount, 0)
                                .set(DocumentIngestQueueEntity::getLastError, null)
                                .set(DocumentIngestQueueEntity::getNextAttemptAt, null)
                                .eq(DocumentIngestQueueEntity::getTenantId, tenantId)
                                .eq(DocumentIngestQueueEntity::getDocumentId, documentId)
                                .eq(DocumentIngestQueueEntity::getStatus, IngestionJobStatus.POISONED))
                == 1;
    }

    public DocumentIngestQueueEntity find(String tenantId, String documentId) {
        return mapper.selectOne(new LambdaQueryWrapper<DocumentIngestQueueEntity>()
                .eq(DocumentIngestQueueEntity::getTenantId, tenantId)
                .eq(DocumentIngestQueueEntity::getDocumentId, documentId)
                .last("LIMIT 1"));
    }

    public List<DocumentIngestQueueEntity> listPoisoned(String tenantId, String datasetId) {
        LambdaQueryWrapper<DocumentIngestQueueEntity> query = new LambdaQueryWrapper<DocumentIngestQueueEntity>()
                .eq(DocumentIngestQueueEntity::getTenantId, tenantId)
                .eq(DocumentIngestQueueEntity::getStatus, IngestionJobStatus.POISONED)
                .orderByDesc(DocumentIngestQueueEntity::getUpdatedAt);
        if (datasetId != null && !datasetId.isBlank()) {
            query.eq(DocumentIngestQueueEntity::getDatasetId, datasetId);
        }
        return mapper.selectList(query);
    }
}

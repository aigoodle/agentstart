package io.aigoodle.knowledge.index;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.knowledge.chunk.Chunk;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.entity.KnowledgeDocumentEntity;
import io.aigoodle.knowledge.entity.SegmentEntity;
import io.aigoodle.knowledge.mapper.SegmentMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;

/**
 * Persists chunks as segments and, for high-quality datasets, embeds and stores them
 * in the dataset's vector store.
 */
public class IndexingService {

    private static final Logger logger = LoggerFactory.getLogger(IndexingService.class);

    private final SegmentMapper segmentMapper;
    private final VectorStoreManager vectorStoreManager;
    private final SegmentIndexDocumentMapper documentMapper;

    public IndexingService(SegmentMapper segmentMapper, VectorStoreManager vectorStoreManager) {
        this.segmentMapper = segmentMapper;
        this.vectorStoreManager = vectorStoreManager;
        this.documentMapper = new SegmentIndexDocumentMapper();
    }

    public int index(DatasetEntity dataset, KnowledgeDocumentEntity document, List<Chunk> chunks) {
        return index(dataset, document, chunks, dataset.getActiveIndexVersionId());
    }

    /** Writes into a rebuilding generation without making it visible to queries. */
    public int index(
            DatasetEntity dataset, KnowledgeDocumentEntity document, List<Chunk> chunks, String indexVersionId) {
        List<Document> vectorDocuments = new ArrayList<>();
        List<String> insertedSegmentIds = new ArrayList<>();
        boolean vectorIndexAvailable = vectorStoreManager.hasVectorIndex(dataset);

        // A retried at-least-once job replaces its own incomplete generation.
        List<SegmentEntity> stale = segmentMapper.selectList(new LambdaQueryWrapper<SegmentEntity>()
                .eq(SegmentEntity::getTenantId, dataset.getTenantId())
                .eq(SegmentEntity::getDocumentId, document.getId())
                .eq(indexVersionId != null, SegmentEntity::getIndexVersionId, indexVersionId)
                .isNull(indexVersionId == null, SegmentEntity::getIndexVersionId));
        if (!stale.isEmpty()) {
            if (vectorIndexAvailable) {
                try {
                    vectorStoreManager
                            .getStore(dataset)
                            .delete(stale.stream()
                                    .map(SegmentEntity::getVectorId)
                                    .filter(java.util.Objects::nonNull)
                                    .toList());
                } catch (Exception exception) {
                    logger.warn(
                            "Failed to clean stale vectors before retrying document {}: {}",
                            document.getId(),
                            exception.getMessage());
                }
            }
            segmentMapper.delete(new LambdaQueryWrapper<SegmentEntity>()
                    .eq(SegmentEntity::getTenantId, dataset.getTenantId())
                    .in(
                            SegmentEntity::getId,
                            stale.stream().map(SegmentEntity::getId).toList()));
        }

        try {
            for (Chunk chunk : chunks) {
                SegmentEntity segment = documentMapper.fromChunk(dataset, document, chunk, indexVersionId);
                segmentMapper.insert(segment);
                insertedSegmentIds.add(segment.getId());
                if (vectorIndexAvailable) {
                    vectorDocuments.add(documentMapper.toVectorDocument(dataset, segment));
                }
            }

            if (!vectorDocuments.isEmpty()) {
                logger.debug(
                        "Embedding and storing {} vectors for document {}", vectorDocuments.size(), document.getId());
                vectorStoreManager.getStore(dataset).add(vectorDocuments);
            }
            return chunks.size();
        } catch (RuntimeException exception) {
            // DB succeeded but vector write failed: remove both sides before retry.
            if (!vectorDocuments.isEmpty()) {
                try {
                    vectorStoreManager
                            .getStore(dataset)
                            .delete(vectorDocuments.stream()
                                    .map(Document::getId)
                                    .toList());
                } catch (Exception cleanupFailure) {
                    logger.warn(
                            "Orphan vector cleanup failed for document {}: {}",
                            document.getId(),
                            cleanupFailure.getMessage());
                }
            }
            if (!insertedSegmentIds.isEmpty()) {
                segmentMapper.delete(new LambdaQueryWrapper<SegmentEntity>()
                        .eq(SegmentEntity::getTenantId, dataset.getTenantId())
                        .in(SegmentEntity::getId, insertedSegmentIds));
            }
            throw exception;
        }
    }

    /** Read paginated segments for a document — powers the frontend "chunks" tab. */
    public List<SegmentEntity> listSegments(String documentId, int page, int pageSize) {
        return listSegments(UserContextHolder.currentTenantId(), documentId, page, pageSize);
    }

    public List<SegmentEntity> listSegments(String tenantId, String documentId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return segmentMapper.selectList(new LambdaQueryWrapper<SegmentEntity>()
                .eq(SegmentEntity::getTenantId, tenantId)
                .eq(SegmentEntity::getDocumentId, documentId)
                .orderByAsc(SegmentEntity::getPosition)
                .last("limit " + pageSize + " offset " + offset));
    }

    public List<SegmentEntity> listSegmentsByDataset(String tenantId, String datasetId) {
        return segmentMapper.selectList(new LambdaQueryWrapper<SegmentEntity>()
                .eq(SegmentEntity::getTenantId, tenantId)
                .eq(SegmentEntity::getDatasetId, datasetId)
                .orderByAsc(SegmentEntity::getDocumentId)
                .orderByAsc(SegmentEntity::getPosition));
    }

    public void removeDocument(DatasetEntity dataset, String documentId) {
        List<SegmentEntity> segments = segmentMapper.selectList(new LambdaQueryWrapper<SegmentEntity>()
                .eq(SegmentEntity::getTenantId, dataset.getTenantId())
                .eq(SegmentEntity::getDocumentId, documentId));
        if (segments.isEmpty()) {
            return;
        }
        if (vectorStoreManager.hasVectorIndex(dataset)) {
            List<String> vectorIds =
                    segments.stream().map(SegmentEntity::getVectorId).toList();
            try {
                vectorStoreManager.getStore(dataset).delete(vectorIds);
            } catch (Exception exception) {
                logger.warn("Failed to delete vectors for document {}: {}", documentId, exception.getMessage());
            }
        }
        segmentMapper.delete(new LambdaQueryWrapper<SegmentEntity>()
                .eq(SegmentEntity::getTenantId, dataset.getTenantId())
                .eq(SegmentEntity::getDocumentId, documentId));
    }

    /** Edit a segment's content: re-embed and swap the vector in the store. */
    public SegmentEntity updateSegment(DatasetEntity dataset, String segmentId, String content) {
        SegmentEntity segment = getSegment(dataset.getTenantId(), segmentId);
        if (segment == null) {
            return null;
        }
        documentMapper.updateContent(segment, content);
        updateOwned(segment);

        if (Boolean.TRUE.equals(segment.getEnabled()) && vectorStoreManager.hasVectorIndex(dataset)) {
            try {
                vectorStoreManager.getStore(dataset).delete(List.of(segment.getVectorId()));
            } catch (Exception exception) {
                logger.warn("Failed to delete old vector for segment {}: {}", segmentId, exception.getMessage());
            }
            vectorStoreManager.getStore(dataset).add(List.of(documentMapper.toVectorDocument(dataset, segment)));
        }
        return segment;
    }

    /** Delete a single segment (and its vector) without touching sibling segments. */
    public void deleteSegment(DatasetEntity dataset, String segmentId) {
        SegmentEntity segment = getSegment(dataset.getTenantId(), segmentId);
        if (segment == null) {
            return;
        }
        if (vectorStoreManager.hasVectorIndex(dataset) && segment.getVectorId() != null) {
            try {
                vectorStoreManager.getStore(dataset).delete(List.of(segment.getVectorId()));
            } catch (Exception exception) {
                logger.warn("Failed to delete vector for segment {}: {}", segmentId, exception.getMessage());
            }
        }
        segmentMapper.delete(new LambdaQueryWrapper<SegmentEntity>()
                .eq(SegmentEntity::getTenantId, dataset.getTenantId())
                .eq(SegmentEntity::getId, segmentId));
    }

    /**
     * Flip a segment's enabled flag: when disabled its vector is removed from the store
     * so it can no longer be retrieved; when re-enabled we re-embed and add it back.
     */
    public SegmentEntity setSegmentEnabled(DatasetEntity dataset, String segmentId, boolean enabled) {
        SegmentEntity segment = getSegment(dataset.getTenantId(), segmentId);
        if (segment == null) {
            return null;
        }
        if (Boolean.TRUE.equals(segment.getEnabled()) == enabled) {
            return segment;
        }
        segment.setEnabled(enabled);
        updateOwned(segment);

        if (vectorStoreManager.hasVectorIndex(dataset)) {
            try {
                if (enabled) {
                    vectorStoreManager
                            .getStore(dataset)
                            .add(List.of(documentMapper.toVectorDocument(dataset, segment)));
                } else {
                    vectorStoreManager.getStore(dataset).delete(List.of(segment.getVectorId()));
                }
            } catch (Exception exception) {
                logger.warn("Failed to sync vector store for segment {}: {}", segmentId, exception.getMessage());
            }
        }
        return segment;
    }

    /** Enables/disables every segment and vector belonging to a document. */
    public int setDocumentEnabled(DatasetEntity dataset, String documentId, boolean enabled) {
        List<SegmentEntity> segments = segmentMapper.selectList(new LambdaQueryWrapper<SegmentEntity>()
                .eq(SegmentEntity::getTenantId, dataset.getTenantId())
                .eq(SegmentEntity::getDocumentId, documentId));
        int changed = 0;
        for (SegmentEntity segment : segments) {
            if (Boolean.TRUE.equals(segment.getEnabled()) == enabled) continue;
            segment.setEnabled(enabled);
            updateOwned(segment);
            changed++;
            if (!vectorStoreManager.hasVectorIndex(dataset)) continue;
            try {
                if (enabled) {
                    vectorStoreManager
                            .getStore(dataset)
                            .add(List.of(documentMapper.toVectorDocument(dataset, segment)));
                } else if (segment.getVectorId() != null) {
                    vectorStoreManager.getStore(dataset).delete(List.of(segment.getVectorId()));
                }
            } catch (Exception exception) {
                logger.warn(
                        "Failed to synchronize document {} segment {} enabled={}: {}",
                        documentId,
                        segment.getId(),
                        enabled,
                        exception.getMessage());
                throw exception;
            }
        }
        return changed;
    }

    public SegmentEntity getSegment(String segmentId) {
        return getSegment(UserContextHolder.currentTenantId(), segmentId);
    }

    public SegmentEntity getSegment(String tenantId, String segmentId) {
        return segmentMapper.selectOne(new LambdaQueryWrapper<SegmentEntity>()
                .eq(SegmentEntity::getTenantId, tenantId)
                .eq(SegmentEntity::getId, segmentId)
                .last("LIMIT 1"));
    }

    /**
     * Append a manually-authored chunk to an existing document, embed it, and
     * insert it into the dataset's vector store. Position is set to
     * {@code max(existing) + 1} so the new chunk sits at the end.
     */
    public SegmentEntity appendSegment(DatasetEntity dataset, KnowledgeDocumentEntity document, String content) {
        List<SegmentEntity> existing = segmentMapper.selectList(new LambdaQueryWrapper<SegmentEntity>()
                .eq(SegmentEntity::getTenantId, dataset.getTenantId())
                .eq(SegmentEntity::getDocumentId, document.getId()));
        int nextPosition = existing.stream()
                        .mapToInt(segment -> segment.getPosition() == null ? 0 : segment.getPosition())
                        .max()
                        .orElse(-1)
                + 1;

        SegmentEntity segment = documentMapper.manualSegment(dataset, document, content, nextPosition);
        segmentMapper.insert(segment);

        if (vectorStoreManager.hasVectorIndex(dataset)) {
            try {
                vectorStoreManager.getStore(dataset).add(List.of(documentMapper.toVectorDocument(dataset, segment)));
            } catch (Exception exception) {
                logger.warn("Failed to add vector for new segment {}: {}", segment.getId(), exception.getMessage());
            }
        }
        return segment;
    }

    /**
     * Re-embed every enabled segment of a document. Segment content stays as-is —
     * this is for "I swapped the embedding model, please refresh the vectors" and
     * for recovering from a partial index failure.
     */
    public int reembedDocument(DatasetEntity dataset, String documentId) {
        List<SegmentEntity> segments = segmentMapper.selectList(new LambdaQueryWrapper<SegmentEntity>()
                .eq(SegmentEntity::getTenantId, dataset.getTenantId())
                .eq(SegmentEntity::getDocumentId, documentId));
        if (segments.isEmpty() || !vectorStoreManager.hasVectorIndex(dataset)) {
            return 0;
        }
        // First evict every old vector — the store might have entries for now-disabled
        // segments too, so drop everything for this document.
        List<String> vectorIds =
                segments.stream().map(SegmentEntity::getVectorId).toList();
        try {
            vectorStoreManager.getStore(dataset).delete(vectorIds);
        } catch (Exception exception) {
            logger.warn("Failed to purge old vectors for document {}: {}", documentId, exception.getMessage());
        }

        List<Document> vectorDocuments = new ArrayList<>();
        for (SegmentEntity segment : segments) {
            if (!Boolean.TRUE.equals(segment.getEnabled())) {
                continue;
            }
            vectorDocuments.add(documentMapper.toVectorDocument(dataset, segment));
        }
        if (!vectorDocuments.isEmpty()) {
            vectorStoreManager.getStore(dataset).add(vectorDocuments);
        }
        return vectorDocuments.size();
    }

    private void updateOwned(SegmentEntity segment) {
        segmentMapper.update(
                segment,
                new LambdaUpdateWrapper<SegmentEntity>()
                        .eq(SegmentEntity::getTenantId, segment.getTenantId())
                        .eq(SegmentEntity::getId, segment.getId()));
    }
}

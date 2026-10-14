package io.aigoodle.knowledge.async;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.knowledge.chunk.Chunk;
import io.aigoodle.knowledge.chunk.ChunkerRegistry;
import io.aigoodle.knowledge.chunk.StructuredDocumentChunker;
import io.aigoodle.knowledge.chunk.TextCleaner;
import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.entity.KnowledgeDocumentEntity;
import io.aigoodle.knowledge.enums.DocumentStatus;
import io.aigoodle.knowledge.index.IndexingService;
import io.aigoodle.knowledge.mapper.DocumentIngestQueueMapper;
import io.aigoodle.knowledge.mapper.KnowledgeDocumentMapper;
import io.aigoodle.knowledge.reader.model.ParsedDocument;
import io.aigoodle.knowledge.service.DatasetCountChange;
import io.aigoodle.knowledge.service.DatasetService;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The actual "extract → clean → chunk → index" work — the same pipeline
 * {@code KnowledgeService.ingest()} runs, but split out so it can be executed
 * either inline (sync fallback) or off a queue consumer (async).
 *
 * <p>Loads context from the DB (dataset + processRule + the sidecar
 * {@link DocumentIngestQueueEntity}), runs the pipeline, updates document
 * status as it progresses, then deletes the sidecar row on success. A crash
 * mid-way leaves the sidecar row in place so a re-run picks up cleanly.</p>
 */
public class DocumentIngestionRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionRunner.class);

    private final DatasetService datasetService;
    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentIngestQueueMapper queueMapper;
    private final ChunkerRegistry chunkerRegistry;
    private final IndexingService indexingService;
    private final StructuredDocumentChunker structuredChunker;
    private final IngestionJobCoordinator coordinator;
    private final String workerId = UUID.randomUUID().toString();

    public DocumentIngestionRunner(
            DatasetService datasetService,
            KnowledgeDocumentMapper documentMapper,
            DocumentIngestQueueMapper queueMapper,
            ChunkerRegistry chunkerRegistry,
            IndexingService indexingService) {
        this(
                datasetService,
                documentMapper,
                queueMapper,
                chunkerRegistry,
                indexingService,
                new IngestionJobCoordinator(queueMapper, Duration.ofMinutes(5), Duration.ofSeconds(2), 5));
    }

    public DocumentIngestionRunner(
            DatasetService datasetService,
            KnowledgeDocumentMapper documentMapper,
            DocumentIngestQueueMapper queueMapper,
            ChunkerRegistry chunkerRegistry,
            IndexingService indexingService,
            IngestionJobCoordinator coordinator) {
        this.datasetService = datasetService;
        this.documentMapper = documentMapper;
        this.queueMapper = queueMapper;
        this.chunkerRegistry = chunkerRegistry;
        this.indexingService = indexingService;
        this.structuredChunker = new StructuredDocumentChunker(chunkerRegistry);
        this.coordinator = coordinator;
    }

    /**
     * Load the sidecar row + document + dataset, run the chunk/index pipeline,
     * update the document row's status as we go. Deletes the sidecar row on
     * success; leaves it in place on failure so the caller (queue impl) can
     * decide to retry or DLQ.
     *
     * @return {@code true} if the document reached {@code COMPLETED};
     *         {@code false} on failure. Never throws — the queue layer needs
     *         a clean boolean to make its retry/DLQ decision.
     */
    public boolean run(String tenantId, String documentId) {
        DocumentIngestQueueEntity task = queueMapper.selectOne(new LambdaQueryWrapper<DocumentIngestQueueEntity>()
                .eq(DocumentIngestQueueEntity::getTenantId, tenantId)
                .eq(DocumentIngestQueueEntity::getDocumentId, documentId)
                .last("LIMIT 1"));
        if (task == null) {
            log.warn("Ingest task {} not found in queue table — probably already processed", documentId);
            return true;
        }
        if (!coordinator.claim(tenantId, documentId, workerId)) {
            log.debug("Ingest task {} is leased, delayed, or isolated", documentId);
            return true;
        }
        KnowledgeDocumentEntity doc = documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getTenantId, tenantId)
                .eq(KnowledgeDocumentEntity::getId, documentId)
                .last("LIMIT 1"));
        if (doc == null) {
            log.warn("Document {} referenced by ingest task no longer exists; dropping task", documentId);
            deleteTask(tenantId, documentId);
            return true;
        }
        DatasetEntity dataset;
        try {
            dataset = datasetService.require(task.getTenantId(), doc.getDatasetId());
        } catch (Exception e) {
            log.error("Dataset {} for document {} not found; marking FAILED", doc.getDatasetId(), documentId);
            markFailed(doc, "dataset not found: " + e.getMessage());
            deleteTask(tenantId, documentId);
            return false;
        }

        try {
            ProcessRule rule = datasetService.processRule(dataset);

            doc.setStatus(DocumentStatus.CHUNKING);
            updateOwned(doc);

            Map<String, Object> baseMetadata = new HashMap<>();
            baseMetadata.put("documentName", doc.getName());
            baseMetadata.put("source", task.getSourceType());
            ParsedDocument parsed = task.getParsedDocumentJson() == null
                    ? null
                    : JsonUtils.parse(task.getParsedDocumentJson(), ParsedDocument.class);
            List<Chunk> chunks = parsed == null
                    ? chunkerRegistry
                            .get(rule.getTemplate())
                            .chunk(TextCleaner.clean(task.getRawText(), rule), rule, baseMetadata)
                    : structuredChunker.chunk(parsed, rule, baseMetadata);

            doc.setStatus(DocumentStatus.INDEXING);
            updateOwned(doc);

            int count = indexingService.index(dataset, doc, chunks);

            doc.setSegmentCount(count);
            doc.setStatus(DocumentStatus.COMPLETED);
            updateOwned(doc);

            datasetService.applyCountChange(dataset, DatasetCountChange.documentAdded(count));
            deleteTask(tenantId, documentId);
            log.info(
                    "Async-ingested document '{}' into dataset {} as {} segments",
                    doc.getName(),
                    doc.getDatasetId(),
                    count);
            return true;
        } catch (Exception e) {
            log.error(
                    "Failed to async-ingest document '{}' into dataset {}: {}",
                    doc.getName(),
                    doc.getDatasetId(),
                    e.getMessage(),
                    e);
            markFailed(doc, e.getMessage());
            coordinator.recordFailure(tenantId, documentId, e.getMessage());
            return false;
        }
    }

    private void markFailed(KnowledgeDocumentEntity doc, String msg) {
        doc.setStatus(DocumentStatus.FAILED);
        doc.setErrorMessage(msg);
        updateOwned(doc);
    }

    private void updateOwned(KnowledgeDocumentEntity document) {
        documentMapper.update(
                document,
                new LambdaUpdateWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getTenantId, document.getTenantId())
                        .eq(KnowledgeDocumentEntity::getId, document.getId()));
    }

    private void deleteTask(String tenantId, String documentId) {
        queueMapper.delete(new LambdaQueryWrapper<DocumentIngestQueueEntity>()
                .eq(DocumentIngestQueueEntity::getTenantId, tenantId)
                .eq(DocumentIngestQueueEntity::getDocumentId, documentId));
    }
}

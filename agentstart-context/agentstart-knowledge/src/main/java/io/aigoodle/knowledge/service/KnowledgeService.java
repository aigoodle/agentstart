package io.aigoodle.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.knowledge.async.DocumentIngestionQueue;
import io.aigoodle.knowledge.chunk.ChunkerRegistry;
import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.entity.HitTestingLogEntity;
import io.aigoodle.knowledge.entity.KnowledgeDocumentEntity;
import io.aigoodle.knowledge.entity.SegmentEntity;
import io.aigoodle.knowledge.index.IndexingService;
import io.aigoodle.knowledge.mapper.DocumentIngestQueueMapper;
import io.aigoodle.knowledge.mapper.HitTestingLogMapper;
import io.aigoodle.knowledge.mapper.KnowledgeDocumentMapper;
import io.aigoodle.knowledge.reader.DocumentExtractor;
import io.aigoodle.knowledge.reader.model.ParsedDocument;
import io.aigoodle.knowledge.retrieve.HybridRetriever;
import io.aigoodle.knowledge.retrieve.RetrievalRequest;
import io.aigoodle.knowledge.retrieve.RetrievedSegment;
import io.aigoodle.rag.api.RagPolicy;
import io.aigoodle.rag.api.RagResult;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Public facade for document ingestion, segment management and knowledge retrieval.
 * The ingestion pipeline itself lives in {@link DocumentIngestionService}; this class
 * keeps the starter's established API while presenting each operation at domain level.
 */
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final DatasetService datasetService;
    private final KnowledgeDocumentMapper documentMapper;
    private final IndexingService indexingService;
    private final HybridRetriever retriever;
    private final DocumentIngestionService ingestionService;

    private HitTestingLogMapper hitTestingLogMapper;

    public KnowledgeService(
            DatasetService datasetService,
            KnowledgeDocumentMapper documentMapper,
            ChunkerRegistry chunkerRegistry,
            IndexingService indexingService,
            HybridRetriever retriever,
            DocumentExtractor extractor) {
        this.datasetService = datasetService;
        this.documentMapper = documentMapper;
        this.indexingService = indexingService;
        this.retriever = retriever;
        this.ingestionService = new DocumentIngestionService(
                datasetService, documentMapper, chunkerRegistry, indexingService, extractor);
    }

    public void setIngestionQueue(DocumentIngestionQueue queue, DocumentIngestQueueMapper queueMapper) {
        ingestionService.configureQueue(queue, queueMapper);
    }

    public void setHitTestingLogMapper(HitTestingLogMapper hitTestingLogMapper) {
        this.hitTestingLogMapper = hitTestingLogMapper;
    }

    public DatasetEntity requireDataset(String tenantId, String datasetId) {
        return datasetService.require(tenantId, datasetId);
    }

    public KnowledgeDocumentEntity requireDocument(String datasetId, String documentId) {
        return requireDocument(UserContextHolder.currentTenantId(), datasetId, documentId);
    }

    public KnowledgeDocumentEntity requireDocument(String tenantId, String datasetId, String documentId) {
        KnowledgeDocumentEntity document = documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getTenantId, tenantId)
                .eq(KnowledgeDocumentEntity::getDatasetId, datasetId)
                .eq(KnowledgeDocumentEntity::getId, documentId)
                .last("LIMIT 1"));
        if (document == null) {
            throw new PlatformException("document_not_found", "Document not found", null);
        }
        return document;
    }

    public SegmentEntity requireSegment(String datasetId, String documentId, String segmentId) {
        return requireSegment(UserContextHolder.currentTenantId(), datasetId, documentId, segmentId);
    }

    public SegmentEntity requireSegment(String tenantId, String datasetId, String documentId, String segmentId) {
        SegmentEntity segment = indexingService.getSegment(tenantId, segmentId);
        if (segment == null
                || !datasetId.equals(segment.getDatasetId())
                || !documentId.equals(segment.getDocumentId())) {
            throw new PlatformException("segment_not_found", "Segment not found", null);
        }
        return segment;
    }

    public KnowledgeDocumentEntity addText(String datasetId, String name, String text) {
        return ingestionService.addText(datasetId, name, text);
    }

    public KnowledgeDocumentEntity addText(String tenantId, String datasetId, String name, String text) {
        requireDataset(tenantId, datasetId);
        return ingestionService.addText(tenantId, datasetId, name, text);
    }

    public KnowledgeDocumentEntity addMarkdown(String datasetId, String name, String markdown) {
        return ingestionService.addMarkdown(datasetId, name, markdown);
    }

    public KnowledgeDocumentEntity addMarkdown(String tenantId, String datasetId, String name, String markdown) {
        requireDataset(tenantId, datasetId);
        return ingestionService.addMarkdown(tenantId, datasetId, name, markdown);
    }

    public KnowledgeDocumentEntity addFile(String datasetId, String filename, byte[] bytes) {
        return ingestionService.addFile(datasetId, filename, bytes);
    }

    public KnowledgeDocumentEntity addFile(String tenantId, String datasetId, String filename, byte[] bytes) {
        requireDataset(tenantId, datasetId);
        return ingestionService.addFile(tenantId, datasetId, filename, bytes);
    }

    public record ChunkPreview(
            String parser,
            String mediaType,
            int pageCount,
            int blockCount,
            List<String> warnings,
            int totalChunks,
            List<ChunkView> chunks) {

        public record ChunkView(int index, String text, int tokens, Map<String, Object> metadata) {}
    }

    public ChunkPreview previewChunks(byte[] bytes, String filename, ProcessRule processRule, int limit) {
        return ingestionService.preview(bytes, filename, processRule, limit);
    }

    public KnowledgeDocumentEntity ingest(String datasetId, String name, String sourceType, String extractedText) {
        return ingestionService.ingest(datasetId, name, sourceType, extractedText);
    }

    public void deleteDocument(String documentId) {
        String tenantId = UserContextHolder.currentTenantId();
        KnowledgeDocumentEntity document = documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getTenantId, tenantId)
                .eq(KnowledgeDocumentEntity::getId, documentId)
                .last("LIMIT 1"));
        if (document == null) {
            return;
        }
        DatasetEntity dataset = datasetService.require(tenantId, document.getDatasetId());
        deleteDocument(dataset, document);
    }

    public void deleteDocument(String tenantId, String datasetId, String documentId) {
        DatasetEntity dataset = requireDataset(tenantId, datasetId);
        KnowledgeDocumentEntity document = requireDocument(tenantId, datasetId, documentId);
        deleteDocument(dataset, document);
    }

    private void deleteDocument(DatasetEntity dataset, KnowledgeDocumentEntity document) {
        indexingService.removeDocument(dataset, document.getId());
        documentMapper.delete(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getTenantId, document.getTenantId())
                .eq(KnowledgeDocumentEntity::getId, document.getId()));
        datasetService.applyCountChange(
                dataset, DatasetCountChange.documentRemoved(valueOrZero(document.getSegmentCount())));
    }

    public List<KnowledgeDocumentEntity> listDocuments(String datasetId) {
        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getTenantId, UserContextHolder.currentTenantId())
                .eq(KnowledgeDocumentEntity::getDatasetId, datasetId));
    }

    public List<KnowledgeDocumentEntity> listDocuments(String tenantId, String datasetId) {
        requireDataset(tenantId, datasetId);
        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getTenantId, tenantId)
                .eq(KnowledgeDocumentEntity::getDatasetId, datasetId));
    }

    public KnowledgeDocumentEntity getDocument(String documentId) {
        return documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getTenantId, UserContextHolder.currentTenantId())
                .eq(KnowledgeDocumentEntity::getId, documentId)
                .last("LIMIT 1"));
    }

    public KnowledgeDocumentEntity getDocument(String tenantId, String datasetId, String documentId) {
        requireDataset(tenantId, datasetId);
        return requireDocument(tenantId, datasetId, documentId);
    }

    public KnowledgeDocumentEntity setDocumentEnabled(
            String tenantId, String datasetId, String documentId, boolean enabled) {
        DatasetEntity dataset = requireDataset(tenantId, datasetId);
        KnowledgeDocumentEntity document = requireDocument(tenantId, datasetId, documentId);
        indexingService.setDocumentEnabled(dataset, documentId, enabled);
        document.setEnabled(enabled);
        updateOwned(document);
        return document;
    }

    public ParsedDocument getParsedDocument(String documentId) {
        return JsonUtils.parse(
                documentMapper.selectParsedDocumentJson(UserContextHolder.currentTenantId(), documentId),
                ParsedDocument.class);
    }

    public ParsedDocument getParsedDocument(String tenantId, String datasetId, String documentId) {
        getDocument(tenantId, datasetId, documentId);
        return JsonUtils.parse(documentMapper.selectParsedDocumentJson(tenantId, documentId), ParsedDocument.class);
    }

    public KnowledgeDocumentEntity reparseDocument(String datasetId, String documentId) {
        String encoded = documentMapper.selectSourceDataBase64(UserContextHolder.currentTenantId(), documentId);
        if (encoded == null || encoded.isBlank()) return null;
        return ingestionService.reparse(
                datasetId, documentId, Base64.getDecoder().decode(encoded));
    }

    public KnowledgeDocumentEntity reparseDocument(String tenantId, String datasetId, String documentId) {
        getDocument(tenantId, datasetId, documentId);
        String encoded = documentMapper.selectSourceDataBase64(tenantId, documentId);
        if (encoded == null || encoded.isBlank()) return null;
        return ingestionService.reparse(
                tenantId, datasetId, documentId, Base64.getDecoder().decode(encoded));
    }

    public List<SegmentEntity> listSegments(String documentId, int page, int pageSize) {
        int pageNumber = Math.max(1, page);
        int boundedPageSize = Math.min(200, Math.max(1, pageSize));
        return indexingService.listSegments(documentId, pageNumber, boundedPageSize);
    }

    public List<SegmentEntity> listSegments(
            String tenantId, String datasetId, String documentId, int page, int pageSize) {
        getDocument(tenantId, datasetId, documentId);
        int pageNumber = Math.max(1, page);
        int boundedPageSize = Math.min(200, Math.max(1, pageSize));
        return indexingService.listSegments(tenantId, documentId, pageNumber, boundedPageSize);
    }

    public SegmentEntity updateSegment(String datasetId, String segmentId, String newContent) {
        DatasetEntity dataset = datasetService.require(datasetId);
        return indexingService.updateSegment(dataset, segmentId, newContent);
    }

    public SegmentEntity updateSegment(
            String tenantId, String datasetId, String documentId, String segmentId, String newContent) {
        DatasetEntity dataset = requireDataset(tenantId, datasetId);
        requireDocument(tenantId, datasetId, documentId);
        requireSegment(tenantId, datasetId, documentId, segmentId);
        return indexingService.updateSegment(dataset, segmentId, newContent);
    }

    public void deleteSegment(String datasetId, String segmentId) {
        DatasetEntity dataset = datasetService.require(datasetId);
        indexingService.deleteSegment(dataset, segmentId);
    }

    public void deleteSegment(String tenantId, String datasetId, String documentId, String segmentId) {
        DatasetEntity dataset = requireDataset(tenantId, datasetId);
        requireDocument(tenantId, datasetId, documentId);
        requireSegment(tenantId, datasetId, documentId, segmentId);
        indexingService.deleteSegment(dataset, segmentId);
    }

    public SegmentEntity setSegmentEnabled(String datasetId, String segmentId, boolean enabled) {
        DatasetEntity dataset = datasetService.require(datasetId);
        return indexingService.setSegmentEnabled(dataset, segmentId, enabled);
    }

    public SegmentEntity setSegmentEnabled(
            String tenantId, String datasetId, String documentId, String segmentId, boolean enabled) {
        DatasetEntity dataset = requireDataset(tenantId, datasetId);
        requireDocument(tenantId, datasetId, documentId);
        requireSegment(tenantId, datasetId, documentId, segmentId);
        return indexingService.setSegmentEnabled(dataset, segmentId, enabled);
    }

    public int reindexDocument(String datasetId, String documentId) {
        DatasetEntity dataset = datasetService.require(datasetId);
        return indexingService.reembedDocument(dataset, documentId);
    }

    public int reindexDocument(String tenantId, String datasetId, String documentId) {
        DatasetEntity dataset = requireDataset(tenantId, datasetId);
        requireDocument(tenantId, datasetId, documentId);
        return indexingService.reembedDocument(dataset, documentId);
    }

    public SegmentEntity appendSegment(String datasetId, String documentId, String content) {
        DatasetEntity dataset = datasetService.require(datasetId);
        KnowledgeDocumentEntity document = requireDocument(UserContextHolder.currentTenantId(), datasetId, documentId);
        if (document == null) {
            return null;
        }

        SegmentEntity segment = indexingService.appendSegment(dataset, document, content);
        document.setSegmentCount(valueOrZero(document.getSegmentCount()) + 1);
        updateOwned(document);
        datasetService.applyCountChange(dataset, DatasetCountChange.segmentAdded());
        return segment;
    }

    public SegmentEntity appendSegment(String tenantId, String datasetId, String documentId, String content) {
        DatasetEntity dataset = requireDataset(tenantId, datasetId);
        KnowledgeDocumentEntity document = requireDocument(tenantId, datasetId, documentId);
        SegmentEntity segment = indexingService.appendSegment(dataset, document, content);
        document.setSegmentCount(valueOrZero(document.getSegmentCount()) + 1);
        updateOwned(document);
        datasetService.applyCountChange(dataset, DatasetCountChange.segmentAdded());
        return segment;
    }

    public List<RetrievedSegment> retrieve(String datasetId, RetrievalRequest request) {
        DatasetEntity dataset = datasetService.require(datasetId);
        return retrieve(dataset, request);
    }

    private List<RetrievedSegment> retrieve(DatasetEntity dataset, RetrievalRequest request) {
        long startedAt = System.currentTimeMillis();
        List<RetrievedSegment> retrievedSegments =
                retriever.retrieve(dataset, datasetService.retrievalConfig(dataset), request);
        recordHitTest(dataset, request, retrievedSegments, (int) (System.currentTimeMillis() - startedAt));
        return retrievedSegments;
    }

    public List<RetrievedSegment> retrieve(String tenantId, String datasetId, RetrievalRequest request) {
        return retrieve(requireDataset(tenantId, datasetId), request);
    }

    public List<RetrievedSegment> retrieve(String datasetId, String query) {
        return retrieve(datasetId, RetrievalRequest.builder().query(query).build());
    }

    public List<RetrievedSegment> retrieve(String tenantId, String datasetId, String query) {
        return retrieve(
                tenantId, datasetId, RetrievalRequest.builder().query(query).build());
    }

    public List<RetrievedSegment> retrieve(List<String> datasetIds, RetrievalRequest request) {
        return retrieve(UserContextHolder.currentTenantId(), datasetIds, request);
    }

    public List<RetrievedSegment> retrieve(String tenantId, List<String> datasetIds, RetrievalRequest request) {
        int resultLimit = request.getTopK() != null ? request.getTopK() : 5;
        return datasetIds.stream()
                .flatMap(datasetId -> retrieve(tenantId, datasetId, request).stream())
                .sorted(Comparator.comparingDouble(RetrievedSegment::getScore).reversed())
                .limit(resultLimit)
                .toList();
    }

    public List<HitTestingLogEntity> listHitTestingHistory(String datasetId, int limit) {
        if (hitTestingLogMapper == null) {
            return List.of();
        }
        int boundedLimit = Math.min(500, Math.max(1, limit));
        return hitTestingLogMapper.selectList(new LambdaQueryWrapper<HitTestingLogEntity>()
                .eq(HitTestingLogEntity::getTenantId, UserContextHolder.currentTenantId())
                .eq(HitTestingLogEntity::getDatasetId, datasetId)
                .orderByDesc(HitTestingLogEntity::getCreatedAt)
                .last("limit " + boundedLimit));
    }

    public List<HitTestingLogEntity> listHitTestingHistory(String tenantId, String datasetId, int limit) {
        requireDataset(tenantId, datasetId);
        if (hitTestingLogMapper == null) return List.of();
        int boundedLimit = Math.min(500, Math.max(1, limit));
        return hitTestingLogMapper.selectList(new LambdaQueryWrapper<HitTestingLogEntity>()
                .eq(HitTestingLogEntity::getTenantId, tenantId)
                .eq(HitTestingLogEntity::getDatasetId, datasetId)
                .orderByDesc(HitTestingLogEntity::getCreatedAt)
                .last("limit " + boundedLimit));
    }

    /** RAG tests share the same durable history as traditional recall tests. */
    public void recordRagHitTest(DatasetEntity dataset, String query, RagPolicy policy, RagResult result) {
        String method = policy.retrieval().channels().contains(RagPolicy.Channel.VECTOR)
                ? (policy.retrieval().channels().contains(RagPolicy.Channel.SPARSE) ? "HYBRID" : "VECTOR")
                : "FULL_TEXT";
        List<RetrievedSegment> segments = result.evidence().stream().map(e -> RetrievedSegment.builder()
                .datasetId(e.datasetId()).documentId(e.documentId()).segmentId(e.segmentId())
                .position(e.position()).content(e.content()).parentContent(e.context())
                .score(e.rerankScore() != null ? e.rerankScore() : e.fusionScore() != null ? e.fusionScore() : e.retrievalScore())
                .vectorScore(e.retrievalScore()).metadata(e.metadata()).build()).toList();
        int latency = (int) Math.min(Integer.MAX_VALUE, result.stageLatencyMillis().values().stream().mapToLong(Long::longValue).sum());
        recordHitTest(dataset, RetrievalRequest.builder().query(query).topK(policy.rerank().topN()).build(), segments, latency, method);
    }

    private void recordHitTest(
            DatasetEntity dataset,
            RetrievalRequest request,
            List<RetrievedSegment> retrievedSegments,
            int latencyMillis) {
        recordHitTest(dataset, request, retrievedSegments, latencyMillis,
                request.getMethod() == null ? null : String.valueOf(request.getMethod()));
    }

    private void recordHitTest(
            DatasetEntity dataset, RetrievalRequest request, List<RetrievedSegment> retrievedSegments,
            int latencyMillis, String method) {
        if (hitTestingLogMapper == null) {
            return;
        }
        try {
            HitTestingLogEntity logEntry = new HitTestingLogEntity();
            logEntry.setTenantId(dataset.getTenantId());
            logEntry.setDatasetId(dataset.getId());
            logEntry.setQuery(request.getQuery());
            logEntry.setMethod(method);
            logEntry.setTopK(request.getTopK());
            logEntry.setHitCount(retrievedSegments == null ? 0 : retrievedSegments.size());
            logEntry.setLatencyMs(latencyMillis);
            logEntry.setResultsJson(JsonUtils.toJson(createResultPreview(retrievedSegments)));
            hitTestingLogMapper.insert(logEntry);
        } catch (Exception exception) {
            log.debug("Failed to record hit-test log: {}", exception.getMessage());
        }
    }

    private static List<Map<String, Object>> createResultPreview(List<RetrievedSegment> retrievedSegments) {
        if (retrievedSegments == null || retrievedSegments.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> preview = new ArrayList<>(retrievedSegments.size());
        for (RetrievedSegment segment : retrievedSegments) {
            Map<String, Object> item = new HashMap<>();
            item.put("segmentId", segment.getSegmentId());
            item.put("documentId", segment.getDocumentId());
            item.put("position", segment.getPosition());
            item.put("score", segment.getScore());
            item.put("vectorScore", segment.getVectorScore());
            item.put(
                    "documentName",
                    segment.getMetadata() == null ? null : segment.getMetadata().get("documentName"));
            item.put(
                    "blockType",
                    segment.getMetadata() == null ? null : segment.getMetadata().get("blockType"));
            item.put(
                    "headingPath",
                    segment.getMetadata() == null ? null : segment.getMetadata().get("heading"));
            String content = segment.getContent();
            item.put("preview", content == null ? null : content.substring(0, Math.min(200, content.length())));
            preview.add(item);
        }
        return preview;
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private void updateOwned(KnowledgeDocumentEntity document) {
        documentMapper.update(
                document,
                new LambdaUpdateWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getTenantId, document.getTenantId())
                        .eq(KnowledgeDocumentEntity::getId, document.getId()));
    }
}

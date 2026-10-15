package io.aigoodle.knowledge.service;

import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.knowledge.async.DocumentIngestQueueEntity;
import io.aigoodle.knowledge.async.DocumentIngestionQueue;
import io.aigoodle.knowledge.async.DocumentIngestionTask;
import io.aigoodle.knowledge.chunk.Chunk;
import io.aigoodle.knowledge.chunk.ChunkerRegistry;
import io.aigoodle.knowledge.chunk.StructuredDocumentChunker;
import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.entity.KnowledgeDocumentEntity;
import io.aigoodle.knowledge.enums.DocumentStatus;
import io.aigoodle.knowledge.enums.IngestionJobStatus;
import io.aigoodle.knowledge.index.IndexingService;
import io.aigoodle.knowledge.mapper.DocumentIngestQueueMapper;
import io.aigoodle.knowledge.mapper.KnowledgeDocumentMapper;
import io.aigoodle.knowledge.reader.DocumentExtractor;
import io.aigoodle.knowledge.reader.model.BlockType;
import io.aigoodle.knowledge.reader.model.DocumentBlock;
import io.aigoodle.knowledge.reader.model.ParsedDocument;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates extraction, chunking, indexing and document status transitions. */
final class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    private static final int DEFAULT_PREVIEW_LIMIT = 10;
    private static final int MAX_PREVIEW_LIMIT = 50;

    private final DatasetService datasetService;
    private final KnowledgeDocumentMapper documentMapper;
    private final ChunkerRegistry chunkerRegistry;
    private final IndexingService indexingService;
    private final DocumentExtractor extractor;
    private final StructuredDocumentChunker structuredChunker;

    private DocumentIngestionQueue ingestionQueue;
    private DocumentIngestQueueMapper queueMapper;

    DocumentIngestionService(
            DatasetService datasetService,
            KnowledgeDocumentMapper documentMapper,
            ChunkerRegistry chunkerRegistry,
            IndexingService indexingService,
            DocumentExtractor extractor) {
        this.datasetService = datasetService;
        this.documentMapper = documentMapper;
        this.chunkerRegistry = chunkerRegistry;
        this.indexingService = indexingService;
        this.extractor = extractor;
        this.structuredChunker = new StructuredDocumentChunker(chunkerRegistry);
    }

    void configureQueue(DocumentIngestionQueue ingestionQueue, DocumentIngestQueueMapper queueMapper) {
        this.ingestionQueue = ingestionQueue;
        this.queueMapper = queueMapper;
    }

    KnowledgeDocumentEntity addText(String datasetId, String name, String text) {
        return addText(UserContextHolder.currentTenantId(), datasetId, name, text);
    }

    KnowledgeDocumentEntity addText(String tenantId, String datasetId, String name, String text) {
        return submit(tenantId, datasetId, name, "text", extractor.extractText(text));
    }

    KnowledgeDocumentEntity addMarkdown(String datasetId, String name, String markdown) {
        return addMarkdown(UserContextHolder.currentTenantId(), datasetId, name, markdown);
    }

    KnowledgeDocumentEntity addMarkdown(String tenantId, String datasetId, String name, String markdown) {
        return submit(tenantId, datasetId, name, "markdown", extractor.extractMarkdown(markdown, name));
    }

    KnowledgeDocumentEntity addFile(String datasetId, String filename, byte[] bytes) {
        return addFile(UserContextHolder.currentTenantId(), datasetId, filename, bytes);
    }

    KnowledgeDocumentEntity addFile(String tenantId, String datasetId, String filename, byte[] bytes) {
        KnowledgeDocumentEntity document =
                submit(tenantId, datasetId, filename, "file", extractor.parseFile(bytes, filename));
        document.setSourceDataBase64(Base64.getEncoder().encodeToString(bytes == null ? new byte[0] : bytes));
        document.setFileSize(bytes == null ? 0L : (long) bytes.length);
        document.setSourceChecksum(sha256(bytes));
        updateOwned(document);
        return document;
    }

    KnowledgeService.ChunkPreview preview(byte[] bytes, String filename, ProcessRule processRule, int limit) {
        ProcessRule effectiveRule = processRule == null ? ProcessRule.naive() : processRule;
        int previewLimit = limit <= 0 ? DEFAULT_PREVIEW_LIMIT : Math.min(limit, MAX_PREVIEW_LIMIT);
        ParsedDocument parsed = extractor.parseFile(bytes, filename);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentName", filename);
        metadata.put("source", "preview");
        List<Chunk> chunks = structuredChunker.chunk(parsed, effectiveRule, metadata);

        List<KnowledgeService.ChunkPreview.ChunkView> preview = new ArrayList<>(Math.min(chunks.size(), previewLimit));
        for (int index = 0; index < chunks.size() && index < previewLimit; index++) {
            Chunk chunk = chunks.get(index);
            String content = chunk.getContent() == null ? "" : chunk.getContent();
            preview.add(new KnowledgeService.ChunkPreview.ChunkView(
                    index + 1, content, chunk.tokenCount(), chunk.getMetadata()));
        }
        return new KnowledgeService.ChunkPreview(
                parsed.getParser(),
                parsed.getMediaType(),
                parsed.pageCount(),
                parsed.getBlocks().size(),
                parsed.getWarnings(),
                chunks.size(),
                preview);
    }

    KnowledgeDocumentEntity ingest(String datasetId, String name, String sourceType, String extractedText) {
        return ingest(datasetId, name, sourceType, plainDocument(name, sourceType, extractedText));
    }

    KnowledgeDocumentEntity ingest(String datasetId, String name, String sourceType, ParsedDocument parsedDocument) {
        return ingest(UserContextHolder.currentTenantId(), datasetId, name, sourceType, parsedDocument);
    }

    KnowledgeDocumentEntity ingest(
            String tenantId, String datasetId, String name, String sourceType, ParsedDocument parsedDocument) {
        DatasetEntity dataset = datasetService.require(tenantId, datasetId);
        DatasetDefinitionFactory.requireEmbeddingModelFor(
                dataset.getIndexingTechnique(), dataset.getEmbeddingModelId());
        String extractedText = parsedDocument == null ? "" : parsedDocument.text();
        KnowledgeDocumentEntity document =
                createDocument(dataset, name, sourceType, extractedText, DocumentStatus.PARSING);
        applyParseDiagnostics(document, parsedDocument);
        updateOwned(document);

        try {
            ProcessRule processRule = datasetService.processRule(dataset);
            updateStatus(document, DocumentStatus.CHUNKING);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("documentName", name);
            metadata.put("source", sourceType);
            List<Chunk> chunks = structuredChunker.chunk(parsedDocument, processRule, metadata);
            updateStatus(document, DocumentStatus.INDEXING);

            int segmentCount = indexingService.index(dataset, document, chunks);
            document.setSegmentCount(segmentCount);
            updateStatus(document, DocumentStatus.COMPLETED);
            datasetService.applyCountChange(dataset, DatasetCountChange.documentAdded(segmentCount));
            log.info("Ingested document '{}' into dataset {} as {} segments", name, datasetId, segmentCount);
        } catch (Exception exception) {
            log.error(
                    "Failed to ingest document '{}' into dataset {}: {}",
                    name,
                    datasetId,
                    exception.getMessage(),
                    exception);
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(exception.getMessage());
            updateOwned(document);
        }
        return document;
    }

    private KnowledgeDocumentEntity submit(String datasetId, String name, String sourceType, String extractedText) {
        return submit(datasetId, name, sourceType, plainDocument(name, sourceType, extractedText));
    }

    private KnowledgeDocumentEntity submit(
            String tenantId, String datasetId, String name, String sourceType, String extractedText) {
        return submit(tenantId, datasetId, name, sourceType, plainDocument(name, sourceType, extractedText));
    }

    private KnowledgeDocumentEntity submit(
            String datasetId, String name, String sourceType, ParsedDocument parsedDocument) {
        return submit(UserContextHolder.currentTenantId(), datasetId, name, sourceType, parsedDocument);
    }

    private KnowledgeDocumentEntity submit(
            String tenantId, String datasetId, String name, String sourceType, ParsedDocument parsedDocument) {
        if (ingestionQueue != null && queueMapper != null) {
            return enqueue(tenantId, datasetId, name, sourceType, parsedDocument);
        }
        return ingest(tenantId, datasetId, name, sourceType, parsedDocument);
    }

    private KnowledgeDocumentEntity enqueue(String datasetId, String name, String sourceType, String extractedText) {
        return enqueue(datasetId, name, sourceType, plainDocument(name, sourceType, extractedText));
    }

    private KnowledgeDocumentEntity enqueue(
            String datasetId, String name, String sourceType, ParsedDocument parsedDocument) {
        return enqueue(UserContextHolder.currentTenantId(), datasetId, name, sourceType, parsedDocument);
    }

    private KnowledgeDocumentEntity enqueue(
            String tenantId, String datasetId, String name, String sourceType, ParsedDocument parsedDocument) {
        DatasetEntity dataset = datasetService.require(tenantId, datasetId);
        DatasetDefinitionFactory.requireEmbeddingModelFor(
                dataset.getIndexingTechnique(), dataset.getEmbeddingModelId());
        String extractedText = parsedDocument == null ? "" : parsedDocument.text();
        String checksum = sha256(extractedText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        KnowledgeDocumentEntity duplicate = documentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getTenantId, dataset.getTenantId())
                        .eq(KnowledgeDocumentEntity::getDatasetId, datasetId)
                        .eq(KnowledgeDocumentEntity::getSourceChecksum, checksum)
                        .ne(KnowledgeDocumentEntity::getStatus, DocumentStatus.FAILED)
                        .last("LIMIT 1"));
        if (duplicate != null) {
            log.info("Deduplicated ingestion submission '{}' against document {}", name, duplicate.getId());
            return duplicate;
        }
        KnowledgeDocumentEntity document =
                createDocument(dataset, name, sourceType, extractedText, DocumentStatus.PENDING);
        document.setSourceChecksum(checksum);
        applyParseDiagnostics(document, parsedDocument);
        updateOwned(document);

        DocumentIngestQueueEntity queuedDocument = new DocumentIngestQueueEntity();
        queuedDocument.setDocumentId(document.getId());
        queuedDocument.setDatasetId(datasetId);
        queuedDocument.setTenantId(dataset.getTenantId());
        queuedDocument.setFilename(name);
        queuedDocument.setSourceType(sourceType);
        queuedDocument.setRawText(extractedText);
        queuedDocument.setParsedDocumentJson(JsonUtils.toJson(parsedDocument));
        queuedDocument.setRetryCount(0);
        queuedDocument.setIdempotencyKey(dataset.getTenantId() + ":" + datasetId + ":" + checksum);
        queuedDocument.setStatus(IngestionJobStatus.READY);
        LocalDateTime now = LocalDateTime.now();
        queuedDocument.setCreatedAt(now);
        queuedDocument.setUpdatedAt(now);
        queueMapper.insert(queuedDocument);

        ingestionQueue.enqueue(new DocumentIngestionTask(dataset.getTenantId(), document.getId(), 0));
        log.info("Queued async ingestion for document '{}' (id={}) into dataset {}", name, document.getId(), datasetId);
        return document;
    }

    private static ParsedDocument plainDocument(String name, String parser, String text) {
        String value = text == null ? "" : text;
        return ParsedDocument.builder()
                .filename(name)
                .parser(parser)
                .blocks(
                        value.isBlank()
                                ? List.of()
                                : List.of(DocumentBlock.builder()
                                        .index(0)
                                        .type(BlockType.PARAGRAPH)
                                        .text(value)
                                        .build()))
                .build();
    }

    private KnowledgeDocumentEntity createDocument(
            DatasetEntity dataset, String name, String sourceType, String extractedText, DocumentStatus initialStatus) {
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setTenantId(dataset.getTenantId());
        document.setDatasetId(dataset.getId());
        document.setName(name);
        document.setSourceType(sourceType);
        document.setStatus(initialStatus);
        document.setEnabled(Boolean.TRUE);
        document.setWordCount(extractedText == null ? 0 : extractedText.length());
        documentMapper.insert(document);
        return document;
    }

    private void updateStatus(KnowledgeDocumentEntity document, DocumentStatus status) {
        document.setStatus(status);
        updateOwned(document);
    }

    private static void applyParseDiagnostics(KnowledgeDocumentEntity document, ParsedDocument parsed) {
        if (parsed == null) return;
        document.setParserName(parsed.getParser());
        document.setMediaType(parsed.getMediaType());
        document.setPageCount(parsed.pageCount());
        document.setBlockCount(
                parsed.getBlocks() == null ? 0 : parsed.getBlocks().size());
        document.setParseWarningsJson(JsonUtils.toJson(parsed.getWarnings()));
        document.setParsedDocumentJson(JsonUtils.toJson(parsed));
    }

    KnowledgeDocumentEntity reparse(String datasetId, String documentId, byte[] sourceBytes) {
        return reparse(UserContextHolder.currentTenantId(), datasetId, documentId, sourceBytes);
    }

    KnowledgeDocumentEntity reparse(String tenantId, String datasetId, String documentId, byte[] sourceBytes) {
        DatasetEntity dataset = datasetService.require(tenantId, datasetId);
        DatasetDefinitionFactory.requireEmbeddingModelFor(
                dataset.getIndexingTechnique(), dataset.getEmbeddingModelId());
        KnowledgeDocumentEntity document = documentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getTenantId, dataset.getTenantId())
                        .eq(KnowledgeDocumentEntity::getId, documentId)
                        .last("LIMIT 1"));
        if (document == null) return null;
        int oldCount = document.getSegmentCount() == null ? 0 : document.getSegmentCount();
        updateStatus(document, DocumentStatus.PARSING);
        ParsedDocument parsed = extractor.parseFile(sourceBytes, document.getName());
        applyParseDiagnostics(document, parsed);
        ProcessRule rule = datasetService.processRule(dataset);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentName", document.getName());
        metadata.put("source", document.getSourceType());
        List<Chunk> chunks = structuredChunker.chunk(parsed, rule, metadata);
        indexingService.removeDocument(dataset, documentId);
        updateStatus(document, DocumentStatus.INDEXING);
        int count = indexingService.index(dataset, document, chunks);
        document.setSegmentCount(count);
        document.setStatus(DocumentStatus.COMPLETED);
        updateOwned(document);
        datasetService.applyCountChange(dataset, new DatasetCountChange(0, count - oldCount));
        return document;
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes == null ? new byte[0] : bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void updateOwned(KnowledgeDocumentEntity document) {
        documentMapper.update(
                document,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getTenantId, document.getTenantId())
                        .eq(KnowledgeDocumentEntity::getId, document.getId()));
    }
}

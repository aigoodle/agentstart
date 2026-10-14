package io.aigoodle.knowledge.index;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.knowledge.chunk.Chunk;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.entity.KnowledgeDocumentEntity;
import io.aigoodle.knowledge.entity.SegmentEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;

/** Maps knowledge segments to their persistence and vector-index representations. */
final class SegmentIndexDocumentMapper {

    SegmentEntity fromChunk(DatasetEntity dataset, KnowledgeDocumentEntity document, Chunk chunk) {
        return fromChunk(dataset, document, chunk, dataset.getActiveIndexVersionId());
    }

    SegmentEntity fromChunk(
            DatasetEntity dataset, KnowledgeDocumentEntity document, Chunk chunk, String indexVersionId) {
        Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
        if (chunk.getParentContent() != null) {
            metadata.put("parentContent", chunk.getParentContent());
        }

        SegmentEntity segment = baseSegment(dataset, document, chunk.getPosition(), chunk.getContent());
        segment.setTokenCount(chunk.tokenCount());
        segment.setIndexVersionId(indexVersionId);
        segment.setMetadataJson(JsonUtils.toJson(metadata));
        return segment;
    }

    SegmentEntity manualSegment(DatasetEntity dataset, KnowledgeDocumentEntity document, String content, int position) {
        SegmentEntity segment = baseSegment(dataset, document, position, content);
        segment.setTokenCount(estimateTokenCount(content));
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentName", document.getName());
        metadata.put("source", "manual");
        segment.setMetadataJson(JsonUtils.toJson(metadata));
        return segment;
    }

    void updateContent(SegmentEntity segment, String content) {
        segment.setContent(content);
        segment.setHash(contentHash(content));
        segment.setTokenCount(estimateTokenCount(content));
    }

    Document toVectorDocument(DatasetEntity dataset, SegmentEntity segment) {
        Map<String, Object> metadata = decodeMetadata(segment.getMetadataJson());
        metadata.put("segmentId", segment.getId());
        metadata.put("datasetId", dataset.getId());
        metadata.put("documentId", segment.getDocumentId());
        metadata.put("position", segment.getPosition());
        if (segment.getIndexVersionId() != null) {
            metadata.put("indexVersionId", segment.getIndexVersionId());
        }
        return Document.builder()
                .id(segment.getVectorId())
                .text(segment.getContent())
                .metadata(metadata)
                .build();
    }

    Map<String, Object> decodeMetadata(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return new HashMap<>(JsonUtils.parseMap(json));
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    private static SegmentEntity baseSegment(
            DatasetEntity dataset, KnowledgeDocumentEntity document, int position, String content) {
        SegmentEntity segment = new SegmentEntity();
        segment.setTenantId(dataset.getTenantId());
        segment.setDatasetId(dataset.getId());
        segment.setDocumentId(document.getId());
        segment.setPosition(position);
        segment.setContent(content);
        segment.setVectorId(UUID.randomUUID().toString());
        segment.setEnabled(Boolean.TRUE);
        segment.setHash(contentHash(content));
        return segment;
    }

    private static int estimateTokenCount(String content) {
        return content == null ? 0 : Math.max(1, content.length() / 4);
    }

    private static String contentHash(String content) {
        return Integer.toHexString(content == null ? 0 : content.hashCode());
    }
}

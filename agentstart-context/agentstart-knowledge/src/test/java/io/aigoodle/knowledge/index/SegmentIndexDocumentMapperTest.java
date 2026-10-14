package io.aigoodle.knowledge.index;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.knowledge.chunk.Chunk;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.entity.KnowledgeDocumentEntity;
import io.aigoodle.knowledge.entity.SegmentEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class SegmentIndexDocumentMapperTest {

    private final SegmentIndexDocumentMapper mapper = new SegmentIndexDocumentMapper();

    @Test
    void keepsParentContentInPersistedAndVectorMetadata() {
        DatasetEntity dataset = dataset("dataset-1");
        KnowledgeDocumentEntity knowledgeDocument = document("document-1");
        Chunk chunk = new Chunk("child text", 3);
        chunk.setParentContent("parent text");

        SegmentEntity segment = mapper.fromChunk(dataset, knowledgeDocument, chunk);
        segment.setId("segment-1");
        Document vectorDocument = mapper.toVectorDocument(dataset, segment);

        assertThat(segment.getMetadataJson()).contains("parent text");
        assertThat(vectorDocument.getMetadata())
                .containsEntry("parentContent", "parent text")
                .containsEntry("segmentId", "segment-1")
                .containsEntry("position", 3);
    }

    @Test
    void rebuildsRequiredMetadataWhenStoredJsonIsCorrupted() {
        DatasetEntity dataset = dataset("dataset-1");
        SegmentEntity segment = new SegmentEntity();
        segment.setId("segment-1");
        segment.setDocumentId("document-1");
        segment.setVectorId("vector-1");
        segment.setPosition(2);
        segment.setContent("readable content");
        segment.setMetadataJson("not-json");

        Document vectorDocument = mapper.toVectorDocument(dataset, segment);

        assertThat(vectorDocument.getMetadata())
                .containsEntry("datasetId", "dataset-1")
                .containsEntry("documentId", "document-1")
                .containsEntry("segmentId", "segment-1");
    }

    private static DatasetEntity dataset(String id) {
        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(id);
        dataset.setTenantId("tenant-1");
        return dataset;
    }

    private static KnowledgeDocumentEntity document(String id) {
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setId(id);
        document.setName("Architecture notes");
        return document;
    }
}

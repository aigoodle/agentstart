package io.aigoodle.knowledge.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.knowledge.chunk.ChunkerRegistry;
import io.aigoodle.knowledge.index.IndexingService;
import io.aigoodle.knowledge.mapper.KnowledgeDocumentMapper;
import io.aigoodle.knowledge.reader.DocumentExtractor;
import io.aigoodle.knowledge.retrieve.HybridRetriever;
import org.junit.jupiter.api.Test;

class KnowledgeServiceTenantTest {

    @Test
    void tenantScopedFacadeRejectsForeignDatasetBeforeReadingDocuments() {
        DatasetService datasets = mock(DatasetService.class);
        when(datasets.require("tenant-a", "dataset-b"))
                .thenThrow(new PlatformException("dataset_not_found", "Dataset not found", null));
        KnowledgeDocumentMapper documents = mock(KnowledgeDocumentMapper.class);
        KnowledgeService knowledge = new KnowledgeService(
                datasets,
                documents,
                mock(ChunkerRegistry.class),
                mock(IndexingService.class),
                mock(HybridRetriever.class),
                mock(DocumentExtractor.class));

        assertThatThrownBy(() -> knowledge.listDocuments("tenant-a", "dataset-b"))
                .hasMessageContaining("Dataset not found");
        assertThatThrownBy(() -> knowledge.addText("tenant-a", "dataset-b", "name", "content"))
                .hasMessageContaining("Dataset not found");

        verifyNoInteractions(documents);
    }
}

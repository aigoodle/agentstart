package io.aigoodle.knowledge.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.enums.IndexingTechnique;
import io.aigoodle.model.service.ModelService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

class VectorStoreManagerTenantTest {

    @Test
    void sameDatasetIdInDifferentTenantsNeverSharesStoreOrEmbeddingModel() {
        ModelService models = mock(ModelService.class);
        EmbeddingModel embeddingA = mock(EmbeddingModel.class);
        EmbeddingModel embeddingB = mock(EmbeddingModel.class);
        when(models.getEmbeddingModel("tenant-a", "embedding-a")).thenReturn(embeddingA);
        when(models.getEmbeddingModel("tenant-b", "embedding-b")).thenReturn(embeddingB);
        VectorStore storeA = mock(VectorStore.class);
        VectorStore storeB = mock(VectorStore.class);
        VectorStoreFactory factory = (dataset, embedding) -> "tenant-a".equals(dataset.getTenantId()) ? storeA : storeB;
        VectorStoreManager manager = new VectorStoreManager(models, factory);

        VectorStore resolvedA = manager.getStore(dataset("tenant-a", "shared-id", "embedding-a"));
        VectorStore resolvedB = manager.getStore(dataset("tenant-b", "shared-id", "embedding-b"));

        assertThat(resolvedA).isSameAs(storeA);
        assertThat(resolvedB).isSameAs(storeB).isNotSameAs(resolvedA);
        verify(models).getEmbeddingModel("tenant-a", "embedding-a");
        verify(models).getEmbeddingModel("tenant-b", "embedding-b");
    }

    private static DatasetEntity dataset(String tenantId, String id, String embeddingModelId) {
        DatasetEntity dataset = new DatasetEntity();
        dataset.setTenantId(tenantId);
        dataset.setId(id);
        dataset.setEmbeddingModelId(embeddingModelId);
        dataset.setIndexingTechnique(IndexingTechnique.HIGH_QUALITY);
        return dataset;
    }
}

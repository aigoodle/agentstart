package io.aigoodle.knowledge.index;

import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.model.service.ModelService;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Provides a {@link VectorStore} per dataset. The default implementation is an
 * in-memory {@link SimpleVectorStore} bound to the dataset's embedding model, so the
 * module works with zero external infrastructure; a custom {@link VectorStoreFactory}
 * bean can swap in Elasticsearch/Milvus/PgVector per dataset.
 * <p>
 * Each dataset owning its own store means dataset isolation is structural — no
 * tenant/dataset filter is needed on similarity search.
 */
public class VectorStoreManager {

    private record StoreKey(String tenantId, String datasetId) {
        private StoreKey {
            tenantId = tenantId == null || tenantId.isBlank() ? "default" : tenantId;
        }
    }

    private final ModelService modelService;
    private final VectorStoreFactory factory;
    private final ConcurrentHashMap<StoreKey, VectorStore> stores = new ConcurrentHashMap<>();

    public VectorStoreManager(ModelService modelService, VectorStoreFactory factory) {
        this.modelService = modelService;
        this.factory = factory;
    }

    public boolean hasVectorIndex(DatasetEntity dataset) {
        return dataset.getEmbeddingModelId() != null
                && !dataset.getEmbeddingModelId().isBlank();
    }

    public VectorStore getStore(DatasetEntity dataset) {
        StoreKey key = new StoreKey(dataset.getTenantId(), dataset.getId());
        return stores.computeIfAbsent(key, ignored -> create(dataset));
    }

    private VectorStore create(DatasetEntity dataset) {
        EmbeddingModel embeddingModel =
                modelService.getEmbeddingModel(dataset.getTenantId(), dataset.getEmbeddingModelId());
        if (factory != null) {
            VectorStore custom = factory.create(dataset, embeddingModel);
            if (custom != null) {
                return custom;
            }
        }
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    public void evict(String datasetId) {
        stores.keySet().removeIf(key -> key.datasetId().equals(datasetId));
    }

    public void evict(String tenantId, String datasetId) {
        stores.remove(new StoreKey(tenantId, datasetId));
    }
}

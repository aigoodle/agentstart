package io.aigoodle.knowledge.milvus;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.index.VectorStoreFactory;
import io.milvus.client.MilvusServiceClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;

/**
 * One Milvus collection per dataset. {@code initializeSchema=true} creates the
 * collection on first write with the dimensions read from the embedding model.
 */
public class MilvusVectorStoreFactory implements VectorStoreFactory {

    private final MilvusServiceClient milvusClient;
    private final MilvusStoreProperties properties;

    public MilvusVectorStoreFactory(MilvusServiceClient milvusClient, MilvusStoreProperties properties) {
        this.milvusClient = milvusClient;
        this.properties = properties;
    }

    @Override
    public VectorStore create(DatasetEntity dataset, EmbeddingModel embeddingModel) {
        MilvusVectorStore.Builder builder = MilvusVectorStore.builder(milvusClient, embeddingModel)
                .collectionName(collectionName(dataset))
                .databaseName(properties.getDatabaseName())
                .embeddingFieldName(properties.getEmbeddingFieldName())
                .indexType(properties.getIndexType())
                .metricType(properties.getMetricType())
                .initializeSchema(true);
        if (properties.getDimensions() > 0) {
            builder.embeddingDimension(properties.getDimensions());
        }

        MilvusVectorStore store = builder.build();
        try {
            store.afterPropertiesSet();
        } catch (Exception e) {
            throw new PlatformException(
                    "milvus_init_failed", "Failed to initialize Milvus store for dataset " + dataset.getId(), e);
        }
        return store;
    }

    private String collectionName(DatasetEntity dataset) {
        String id = dataset.getId() == null ? "default" : dataset.getId();
        return properties.getCollectionPrefix()
                + id.replaceAll("[^A-Za-z0-9_]", "_").toLowerCase();
    }
}

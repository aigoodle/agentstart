package io.aigoodle.knowledge.elasticsearch;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.index.VectorStoreFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;

/**
 * One Elasticsearch index per dataset; the {@link ElasticsearchVectorStore} is
 * instantiated on demand by {@link io.aigoodle.knowledge.index.VectorStoreManager}
 * and its {@code initializeSchema=true} flag creates the mapping on first write.
 */
public class ElasticsearchVectorStoreFactory implements VectorStoreFactory {

    private final Rest5Client restClient;
    private final ElasticsearchStoreProperties properties;

    public ElasticsearchVectorStoreFactory(Rest5Client restClient, ElasticsearchStoreProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public VectorStore create(DatasetEntity dataset, EmbeddingModel embeddingModel) {
        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setIndexName(indexName(dataset, properties));
        options.setSimilarity(properties.getSimilarity());
        if (properties.getDimensions() > 0) {
            options.setDimensions(properties.getDimensions());
        }

        ElasticsearchVectorStore store = ElasticsearchVectorStore.builder(restClient, embeddingModel)
                .options(options)
                .initializeSchema(true)
                .build();
        try {
            store.afterPropertiesSet();
        } catch (Exception e) {
            throw new PlatformException(
                    "elasticsearch_init_failed",
                    "Failed to initialize Elasticsearch store for dataset " + dataset.getId(),
                    e);
        }
        return store;
    }

    static String indexName(DatasetEntity dataset, ElasticsearchStoreProperties properties) {
        String id = dataset.getId() == null ? "default" : dataset.getId();
        return properties.getIndexPrefix()
                + id.replaceAll("[^A-Za-z0-9_-]", "-").toLowerCase();
    }
}

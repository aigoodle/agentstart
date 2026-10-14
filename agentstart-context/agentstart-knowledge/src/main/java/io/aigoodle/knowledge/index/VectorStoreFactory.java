package io.aigoodle.knowledge.index;

import io.aigoodle.knowledge.entity.DatasetEntity;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Extension point for plugging an external vector store (Elasticsearch, Milvus,
 * PgVector, ...) per dataset. Return {@code null} to fall back to the in-memory
 * default. Publish one implementation as a Spring bean to override globally.
 */
public interface VectorStoreFactory {

    VectorStore create(DatasetEntity dataset, EmbeddingModel embeddingModel);
}

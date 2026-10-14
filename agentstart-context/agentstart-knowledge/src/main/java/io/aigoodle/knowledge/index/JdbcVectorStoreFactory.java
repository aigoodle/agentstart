package io.aigoodle.knowledge.index;

import io.aigoodle.knowledge.entity.DatasetEntity;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Builds a {@link JdbcVectorStore} per dataset so embeddings are persisted in the same
 * relational database as the rest of the data. Enabled by publishing this as a bean
 * (the auto-configuration does so when {@code spring-agent.knowledge.vector-store=jdbc}).
 */
public class JdbcVectorStoreFactory implements VectorStoreFactory {

    public static final String DEFAULT_TABLE = "goodle_embeddings";

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;

    public JdbcVectorStoreFactory(JdbcTemplate jdbcTemplate, String tableName) {
        this.jdbcTemplate = jdbcTemplate;
        String configuredTableName = tableName == null || tableName.isBlank() ? DEFAULT_TABLE : tableName;
        this.tableName = JdbcVectorStoreConfiguration.requireValidTableName(configuredTableName);
    }

    @Override
    public VectorStore create(DatasetEntity dataset, EmbeddingModel embeddingModel) {
        JdbcVectorStoreConfiguration configuration = new JdbcVectorStoreConfiguration(dataset.getId(), tableName);
        return new JdbcVectorStore(jdbcTemplate, embeddingModel, configuration);
    }
}

package io.aigoodle.knowledge.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class JdbcVectorStoreConfigurationTest {

    @Test
    void acceptsSimpleAndSchemaQualifiedTableNames() {
        assertThat(new JdbcVectorStoreConfiguration("dataset-1", "goodle_embeddings").tableName())
                .isEqualTo("goodle_embeddings");
        assertThat(new JdbcVectorStoreConfiguration("dataset-1", "agent.embeddings").tableName())
                .isEqualTo("agent.embeddings");
    }

    @Test
    void rejectsTableNamesThatCouldChangeTheSqlStatement() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JdbcVectorStoreConfiguration("dataset-1", "embeddings; DROP TABLE dataset"))
                .withMessageContaining("Invalid vector table name");
    }

    @Test
    void requiresADatasetScope() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JdbcVectorStoreConfiguration(" ", "goodle_embeddings"))
                .withMessage("datasetId must not be blank");
    }
}

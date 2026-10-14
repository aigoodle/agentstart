package io.aigoodle.knowledge.index;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcVectorStoreDeleteTest {

    @Test
    void scopesDeletionToTheConfiguredDataset() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcVectorStore vectorStore = new JdbcVectorStore(
                jdbcTemplate,
                mock(EmbeddingModel.class),
                new JdbcVectorStoreConfiguration("dataset-1", "goodle_embeddings"));

        vectorStore.delete(List.of("document-1", "document-2"));

        verify(jdbcTemplate)
                .update(
                        "DELETE FROM goodle_embeddings WHERE dataset_id = ? AND id IN (?,?)",
                        "dataset-1",
                        "document-1",
                        "document-2");
    }
}

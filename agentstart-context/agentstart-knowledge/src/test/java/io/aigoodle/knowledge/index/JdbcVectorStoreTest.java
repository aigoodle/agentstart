package io.aigoodle.knowledge.index;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.knowledge.KnowledgeTestApplication;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.enums.IndexingTechnique;
import io.aigoodle.knowledge.retrieve.RetrievedSegment;
import io.aigoodle.knowledge.service.CreateDatasetRequest;
import io.aigoodle.knowledge.service.DatasetService;
import io.aigoodle.knowledge.service.KnowledgeService;
import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.service.ModelRegistration;
import io.aigoodle.model.service.ModelService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the JDBC vector store: embeddings are persisted into the relational
 * database and drive retrieval (no in-memory store, no pgvector).
 */
@SpringBootTest(classes = KnowledgeTestApplication.class, properties = "spring-agent.knowledge.vector-store=jdbc")
class JdbcVectorStoreTest {

    @Autowired
    private ModelService modelService;

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VectorStoreFactory vectorStoreFactory;

    @Test
    void embeddingsPersistInDatabaseAndDriveRetrieval() {
        assertInstanceOf(
                JdbcVectorStoreFactory.class, vectorStoreFactory, "the jdbc vector store factory should be active");

        ModelEntity emb = modelService.register(ModelRegistration.builder()
                .tenantId("jdbc")
                .providerName("fake")
                .modelName("hash-embed")
                .modelType(ModelType.TEXT_EMBEDDING)
                .credentials(Map.of("dimensions", 128))
                .build());
        DatasetEntity ds = datasetService.create(CreateDatasetRequest.builder()
                .tenantId("jdbc")
                .name("kb")
                .embeddingModelId(emb.getId())
                .indexingTechnique(IndexingTechnique.HIGH_QUALITY)
                .build());

        knowledgeService.addText(
                ds.getTenantId(),
                ds.getId(),
                "animals.txt",
                "Dogs are loyal animals that love playing fetch.\n"
                        + "Python is a popular programming language for data science.");

        // vectors landed in the relational table
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM goodle_embeddings WHERE dataset_id = ?", Integer.class, ds.getId());
        assertNotNull(rows);
        assertTrue(rows > 0, "embeddings should be persisted in goodle_embeddings table");

        // retrieval works through the persisted store
        List<RetrievedSegment> hits = knowledgeService.retrieve(ds.getTenantId(), ds.getId(), "dogs playing fetch");
        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).getContent().toLowerCase().contains("dog"));
        assertTrue(hits.get(0).getVectorScore() > 0, "vector score should come from the JDBC store");
    }
}

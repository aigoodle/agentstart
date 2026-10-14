package io.aigoodle.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.knowledge.KnowledgeTestApplication;
import io.aigoodle.knowledge.async.DocumentIngestQueueEntity;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.entity.KnowledgeDocumentEntity;
import io.aigoodle.knowledge.enums.DocumentStatus;
import io.aigoodle.knowledge.enums.IndexingTechnique;
import io.aigoodle.knowledge.mapper.DocumentIngestQueueMapper;
import io.aigoodle.knowledge.mapper.KnowledgeDocumentMapper;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.service.ModelRegistration;
import io.aigoodle.model.service.ModelService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Proves the async ingest path (sync fallback flavour — no broker needed for
 * the test) reaches {@code COMPLETED} without blocking the caller, that the
 * sidecar {@code document_ingest_queue} row is cleaned up on success, and
 * that segments end up in the vector store just like the sync path.
 *
 * <p>Run flavour: SpringBootTest with
 * {@code spring-agent.knowledge.async.enabled=true} — the auto-config sees no
 * RabbitTemplate on the classpath (starter-amqp is optional; we don't pull
 * it into the test), so it wires the in-memory {@code SyncDocumentIngestionQueue}
 * pool. That still exercises the whole async surface end-to-end.</p>
 */
@SpringBootTest(classes = KnowledgeTestApplication.class)
@TestPropertySource(
        properties = {
            "spring-agent.knowledge.async.enabled=true",
            "spring-agent.knowledge.async.worker-threads=2",
            "spring-agent.knowledge.vector-store=jdbc",
        })
class AsyncIngestionTest {

    @Autowired
    private ModelService modelService;

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    @Autowired
    private DocumentIngestQueueMapper queueMapper;

    private String embeddingModelId() {
        var m = modelService.register(ModelRegistration.builder()
                .tenantId("async-test")
                .providerName("fake")
                .modelName("hash-embed")
                .modelType(ModelType.TEXT_EMBEDDING)
                .credentials(Map.of("dimensions", 256))
                .build());
        return m.getId();
    }

    @Test
    void addTextAsyncCompletesAndCleansUpSidecar() throws Exception {
        assumeTrue(true, "Runs against sync fallback — no broker required.");
        DatasetEntity ds = datasetService.create(CreateDatasetRequest.builder()
                .tenantId("async-test")
                .name("async-ds")
                .embeddingModelId(embeddingModelId())
                .indexingTechnique(IndexingTechnique.HIGH_QUALITY)
                .build());

        // addText now enqueues + returns immediately when async is on.
        KnowledgeDocumentEntity doc = knowledgeService.addText(
                ds.getTenantId(),
                ds.getId(),
                "sample.txt",
                "Alpha beta gamma. Delta epsilon zeta. Eta theta iota kappa. "
                        + "Repeat: alpha beta gamma delta epsilon zeta eta theta.");
        assertNotNull(doc.getId());
        assertEquals(
                DocumentStatus.PENDING, doc.getStatus(), "Async submission must return with the doc in PENDING state");

        KnowledgeDocumentEntity duplicate = knowledgeService.addText(
                ds.getTenantId(),
                ds.getId(),
                "copy.txt",
                "Alpha beta gamma. Delta epsilon zeta. Eta theta iota kappa. "
                        + "Repeat: alpha beta gamma delta epsilon zeta eta theta.");
        assertEquals(doc.getId(), duplicate.getId(), "Identical async submissions must be idempotent");

        // Sidecar row must be present so the worker has raw_text to consume.
        DocumentIngestQueueEntity task = queueMapper.selectOne(new LambdaQueryWrapper<DocumentIngestQueueEntity>()
                .eq(DocumentIngestQueueEntity::getTenantId, ds.getTenantId())
                .eq(DocumentIngestQueueEntity::getDocumentId, doc.getId())
                .last("LIMIT 1"));
        assertNotNull(task, "Sidecar queue row must exist right after enqueue");
        assertTrue(task.getRawText() != null && !task.getRawText().isBlank());

        // Wait up to 5s for the sync fallback worker to finish. 5s is plenty
        // for the fake embedder — real embeddings would need longer.
        DocumentStatus terminal = null;
        for (int i = 0; i < 50 && terminal == null; i++) {
            Thread.sleep(100);
            var reloaded = documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                    .eq(KnowledgeDocumentEntity::getTenantId, ds.getTenantId())
                    .eq(KnowledgeDocumentEntity::getId, doc.getId())
                    .last("LIMIT 1"));
            var s = reloaded.getStatus();
            if (s == DocumentStatus.COMPLETED || s == DocumentStatus.FAILED) terminal = s;
        }
        assertEquals(DocumentStatus.COMPLETED, terminal, "Async ingestion must reach COMPLETED within the wait window");

        // Sidecar row should be gone — the runner deletes it on success.
        assertEquals(
                0,
                queueMapper
                        .selectCount(new LambdaQueryWrapper<DocumentIngestQueueEntity>()
                                .eq(DocumentIngestQueueEntity::getTenantId, ds.getTenantId()))
                        .intValue(),
                "Successful run must delete the sidecar queue row");

        var reloaded = documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getTenantId, ds.getTenantId())
                .eq(KnowledgeDocumentEntity::getId, doc.getId())
                .last("LIMIT 1"));
        assertTrue(
                reloaded.getSegmentCount() != null && reloaded.getSegmentCount() > 0,
                "Document must land with a segment count");
    }
}

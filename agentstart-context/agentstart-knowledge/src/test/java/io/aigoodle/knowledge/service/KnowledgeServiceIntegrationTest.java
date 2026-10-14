package io.aigoodle.knowledge.service;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.knowledge.KnowledgeTestApplication;
import io.aigoodle.knowledge.async.DocumentIngestQueueEntity;
import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.config.RetrievalConfig;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.entity.HitTestingLogEntity;
import io.aigoodle.knowledge.entity.KnowledgeDocumentEntity;
import io.aigoodle.knowledge.entity.SegmentEntity;
import io.aigoodle.knowledge.enums.ChunkingTemplate;
import io.aigoodle.knowledge.enums.IndexingTechnique;
import io.aigoodle.knowledge.enums.RetrievalMethod;
import io.aigoodle.knowledge.mapper.DocumentIngestQueueMapper;
import io.aigoodle.knowledge.mapper.HitTestingLogMapper;
import io.aigoodle.knowledge.mapper.KnowledgeDocumentMapper;
import io.aigoodle.knowledge.mapper.SegmentMapper;
import io.aigoodle.knowledge.retrieve.RetrievalRequest;
import io.aigoodle.knowledge.retrieve.RetrievedSegment;
import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.service.ModelRegistration;
import io.aigoodle.model.service.ModelService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = KnowledgeTestApplication.class)
class KnowledgeServiceIntegrationTest {
    @Autowired
    private org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory;

    @Autowired
    private ModelService modelService;

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    @Autowired
    private SegmentMapper segmentMapper;

    @Autowired
    private DocumentIngestQueueMapper queueMapper;

    @Autowired
    private HitTestingLogMapper hitTestingLogMapper;

    private static final String DOC = String.join(
            "\n",
            "Cats are independent pets that enjoy sleeping all day.",
            "Dogs are loyal animals and love playing fetch outdoors.",
            "Paris is the capital city of France and very beautiful.",
            "Python is a popular programming language for data science.");

    private String registerEmbeddingModel() {
        ModelEntity m = modelService.register(ModelRegistration.builder()
                .tenantId("kb")
                .providerName("fake")
                .modelName("hash-embed")
                .modelType(ModelType.TEXT_EMBEDDING)
                .credentials(Map.of("dimensions", 256))
                .build());
        return m.getId();
    }

    private DatasetEntity highQualityDataset(ChunkingTemplate template) {
        ProcessRule rule = new ProcessRule();
        rule.setTemplate(template);
        rule.setChunkTokens(12);
        rule.setOverlapTokens(2);
        rule.setParentChunkTokens(40);
        return datasetService.create(CreateDatasetRequest.builder()
                .tenantId("kb")
                .name("kb-" + template)
                .embeddingModelId(registerEmbeddingModel())
                .indexingTechnique(IndexingTechnique.HIGH_QUALITY)
                .processRule(rule)
                .retrievalConfig(RetrievalConfig.vector())
                .build());
    }

    @Test
    void ingestAndHybridRetrieve() {
        DatasetEntity ds = highQualityDataset(ChunkingTemplate.NAIVE);
        var doc = knowledgeService.addText(ds.getTenantId(), ds.getId(), "animals.txt", DOC);
        assertEquals("COMPLETED", doc.getStatus().name());
        assertTrue(doc.getSegmentCount() > 0);

        List<RetrievedSegment> dogs = knowledgeService.retrieve(ds.getTenantId(), ds.getId(), "dogs loyal fetch");
        assertFalse(dogs.isEmpty(), "should retrieve something for 'dogs'");
        assertTrue(
                dogs.get(0).getContent().toLowerCase().contains("dog"),
                "top hit should be the dogs chunk, was: " + dogs.get(0).getContent());

        List<RetrievedSegment> py =
                knowledgeService.retrieve(ds.getTenantId(), ds.getId(), "python programming language");
        assertTrue(
                py.get(0).getContent().toLowerCase().contains("python"),
                "top hit should be the python chunk, was: " + py.get(0).getContent());

        // vector retrieval exposes the similarity score
        assertTrue(dogs.get(0).getScore() > 0);
        assertTrue(dogs.get(0).getVectorScore() > 0);
    }

    @Test
    void persistenceGuardRejectsCrossTenantDatasetAccess() {
        assertTrue(
                sqlSessionFactory.getConfiguration().getInterceptors().stream()
                        .anyMatch(interceptor ->
                                interceptor instanceof io.aigoodle.persistence.TenantSqlGuardInterceptor),
                () -> "Registered SQL interceptors: "
                        + sqlSessionFactory.getConfiguration().getInterceptors());
        UserContextHolder.set(
                CurrentUser.builder().userId("knowledge-test").tenantId("kb-a").build());
        try {
            DatasetEntity owned = datasetService.create(CreateDatasetRequest.builder()
                    .tenantId("kb-a")
                    .name("guarded-kb")
                    .indexingTechnique(IndexingTechnique.HIGH_QUALITY)
                    .embeddingModelId("test-embedding")
                    .build());
            assertTenantMismatch(() -> datasetService.require("kb-b", owned.getId()));
            assertTenantMismatch(() -> datasetService.create(CreateDatasetRequest.builder()
                    .tenantId("kb-b")
                    .name("forged-kb")
                    .indexingTechnique(IndexingTechnique.HIGH_QUALITY)
                    .embeddingModelId("test-embedding")
                    .build()));
            assertTenantMismatch(() -> documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                    .eq(KnowledgeDocumentEntity::getTenantId, "kb-b")));
            assertTenantMismatch(() -> segmentMapper.selectList(
                    new LambdaQueryWrapper<SegmentEntity>().eq(SegmentEntity::getTenantId, "kb-b")));
            assertTenantMismatch(() -> queueMapper.selectList(new LambdaQueryWrapper<DocumentIngestQueueEntity>()
                    .eq(DocumentIngestQueueEntity::getTenantId, "kb-b")));
            assertTenantMismatch(() -> hitTestingLogMapper.selectList(
                    new LambdaQueryWrapper<HitTestingLogEntity>().eq(HitTestingLogEntity::getTenantId, "kb-b")));
            assertTenantMismatch(() -> documentMapper.selectById("unguarded-id"));
        } finally {
            UserContextHolder.clear();
        }
    }

    private static void assertTenantMismatch(org.junit.jupiter.api.function.Executable executable) {
        Throwable failure = assertThrows(RuntimeException.class, executable);
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        assertInstanceOf(SecurityException.class, root);
    }

    @Test
    void vectorRetrievalWorks() {
        DatasetEntity ds = highQualityDataset(ChunkingTemplate.NAIVE);
        knowledgeService.addText(ds.getTenantId(), ds.getId(), "animals.txt", DOC);

        var vec = knowledgeService.retrieve(
                ds.getTenantId(),
                ds.getId(),
                RetrievalRequest.builder()
                        .query("capital of France Paris")
                        .method(RetrievalMethod.VECTOR)
                        .build());
        assertTrue(vec.get(0).getContent().toLowerCase().contains("paris"));
    }

    @Test
    void parentChildRetrievalReturnsParentContext() {
        DatasetEntity ds = highQualityDataset(ChunkingTemplate.PARENT_CHILD);
        knowledgeService.addText(ds.getTenantId(), ds.getId(), "animals.txt", DOC.repeat(2));

        var res = knowledgeService.retrieve(ds.getTenantId(), ds.getId(), "dogs loyal fetch");
        assertFalse(res.isEmpty());
        RetrievedSegment top = res.get(0);
        assertNotNull(top.getParentContent(), "parent-child chunks must carry parent context");
        assertTrue(top.contextText().length() >= top.getContent().length());
    }

    @Test
    void metadataFilterRestrictsResults() {
        DatasetEntity ds = highQualityDataset(ChunkingTemplate.NAIVE);
        knowledgeService.addText(ds.getTenantId(), ds.getId(), "animals.txt", DOC);
        knowledgeService.addText(
                ds.getTenantId(), ds.getId(), "other.txt", "Dogs also appear in this second document.");

        var filtered = knowledgeService.retrieve(
                ds.getTenantId(),
                ds.getId(),
                RetrievalRequest.builder()
                        .query("dogs")
                        .metadataFilter(Map.of("documentName", "other.txt"))
                        .build());
        assertFalse(filtered.isEmpty());
        assertTrue(filtered.stream()
                .allMatch(r -> "other.txt".equals(r.getMetadata().get("documentName"))));
    }

    @Test
    void deleteDocumentRemovesSegments() {
        DatasetEntity ds = highQualityDataset(ChunkingTemplate.NAIVE);
        var doc = knowledgeService.addText(ds.getTenantId(), ds.getId(), "animals.txt", DOC);
        assertFalse(knowledgeService
                .retrieve(ds.getTenantId(), ds.getId(), "python")
                .isEmpty());

        knowledgeService.deleteDocument(ds.getTenantId(), ds.getId(), doc.getId());
        assertTrue(
                knowledgeService
                        .retrieve(ds.getTenantId(), ds.getId(), "python")
                        .isEmpty(),
                "after deletion nothing should be retrievable");
    }

    @Test
    void documentEnabledFlagControlsAllRetrievalPaths() {
        DatasetEntity ds = highQualityDataset(ChunkingTemplate.NAIVE);
        var doc = knowledgeService.addText(ds.getTenantId(), ds.getId(), "toggle.txt", DOC);
        assertFalse(knowledgeService
                .retrieve(ds.getTenantId(), ds.getId(), "python")
                .isEmpty());

        var disabled = knowledgeService.setDocumentEnabled(ds.getTenantId(), ds.getId(), doc.getId(), false);
        assertFalse(disabled.getEnabled());
        assertTrue(knowledgeService
                .retrieve(ds.getTenantId(), ds.getId(), "python")
                .isEmpty());

        var enabled = knowledgeService.setDocumentEnabled(ds.getTenantId(), ds.getId(), doc.getId(), true);
        assertTrue(enabled.getEnabled());
        assertFalse(knowledgeService
                .retrieve(ds.getTenantId(), ds.getId(), "python")
                .isEmpty());
    }

    @Test
    void retrievalCanExpandAdjacentChunkContext() {
        DatasetEntity ds = highQualityDataset(ChunkingTemplate.NAIVE);
        RetrievalConfig config = RetrievalConfig.vector();
        config.setNeighborWindow(1);
        UpdateDatasetRequest patch = new UpdateDatasetRequest();
        patch.setRetrievalConfig(config);
        datasetService.update(ds.getTenantId(), ds.getId(), patch);
        knowledgeService.addText(ds.getTenantId(), ds.getId(), "animals.txt", DOC);

        RetrievedSegment hit = knowledgeService
                .retrieve(ds.getTenantId(), ds.getId(), "dogs loyal fetch")
                .get(0);
        assertNotNull(hit.getExpandedContext());
        assertTrue(hit.contextText().length() >= hit.getContent().length());
    }
}

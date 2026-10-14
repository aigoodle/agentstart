package io.aigoodle.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.aigoodle.knowledge.chunk.ChunkerRegistry;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.entity.HitTestingLogEntity;
import io.aigoodle.knowledge.index.IndexingService;
import io.aigoodle.knowledge.mapper.HitTestingLogMapper;
import io.aigoodle.knowledge.mapper.KnowledgeDocumentMapper;
import io.aigoodle.knowledge.reader.DocumentExtractor;
import io.aigoodle.knowledge.retrieve.HybridRetriever;
import io.aigoodle.rag.api.Evidence;
import io.aigoodle.rag.api.QueryPlan;
import io.aigoodle.rag.api.RagPolicy;
import io.aigoodle.rag.api.RagResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeServiceRagHistoryTest {
    @Test
    void writesRagRunsToTheSameTenantScopedRecallHistory() {
        KnowledgeService service = new KnowledgeService(mock(DatasetService.class), mock(KnowledgeDocumentMapper.class),
                mock(ChunkerRegistry.class), mock(IndexingService.class), mock(HybridRetriever.class), mock(DocumentExtractor.class));
        HitTestingLogMapper mapper = mock(HitTestingLogMapper.class);
        service.setHitTestingLogMapper(mapper);
        DatasetEntity dataset = new DatasetEntity();
        dataset.setTenantId("tenant-a");
        dataset.setId("ds");
        RagPolicy defaults = RagPolicy.defaults();
        RagPolicy policy = new RagPolicy(defaults.queryPlanning(),
                new RagPolicy.Retrieval(List.of(RagPolicy.Channel.VECTOR, RagPolicy.Channel.SPARSE), 30, 4, Map.of()),
                defaults.fusion(), defaults.rerank(), defaults.context(), defaults.budget());
        Evidence evidence = new Evidence("ds", "doc", "segment", 1, "source text", null, .8, .04, .9,
                List.of("q0"), Map.of("documentName", "manual.pdf"));
        RagResult result = new RagResult("question", new QueryPlan("question", List.of()), List.of(evidence), Map.of("retrieval", 12L, "planning", 3L));
        service.recordRagHitTest(dataset, "question", policy, result);
        ArgumentCaptor<HitTestingLogEntity> entry = ArgumentCaptor.forClass(HitTestingLogEntity.class);
        verify(mapper).insert(entry.capture());
        assertThat(entry.getValue().getTenantId()).isEqualTo("tenant-a");
        assertThat(entry.getValue().getDatasetId()).isEqualTo("ds");
        assertThat(entry.getValue().getQuery()).isEqualTo("question");
        assertThat(entry.getValue().getMethod()).isEqualTo("HYBRID");
        assertThat(entry.getValue().getHitCount()).isEqualTo(1);
        assertThat(entry.getValue().getLatencyMs()).isEqualTo(15);
        assertThat(entry.getValue().getResultsJson()).contains("segment", "manual.pdf", "0.9");
    }
}

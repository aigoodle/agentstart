package io.aigoodle.knowledge.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.knowledge.config.RetrievalConfig;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.retrieve.HybridRetriever;
import io.aigoodle.knowledge.service.DatasetService;
import io.aigoodle.rag.api.PlannedQuery;
import io.aigoodle.rag.api.RagPolicy;
import io.aigoodle.rag.api.RagRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeVectorRetrieverTest {
    @Test
    void reusesWorkingRetrieverWithTheDraftThresholdCandidatePoolAndContextSettings() {
        DatasetService service = mock(DatasetService.class);
        HybridRetriever existing = mock(HybridRetriever.class);
        DatasetEntity dataset = new DatasetEntity();
        RetrievalConfig saved = new RetrievalConfig();
        saved.setScoreThreshold(.1);
        saved.setMaxChunksPerDocument(3);
        when(service.require("tenant", "ds")).thenReturn(dataset);
        when(service.retrievalConfig(dataset)).thenReturn(saved);
        when(existing.retrieve(eq(dataset), any(), any())).thenReturn(List.of());
        RagPolicy defaults = RagPolicy.defaults();
        RagPolicy policy = new RagPolicy(defaults.queryPlanning(),
                new RagPolicy.Retrieval(List.of(RagPolicy.Channel.VECTOR), 42, 4, Map.of(), .75, 2),
                defaults.fusion(), defaults.rerank(),
                new RagPolicy.Context(true, 2, true, false, null, 6000), defaults.budget());
        new KnowledgeVectorRetriever(service, existing).retrieve(
                new PlannedQuery("q0", "question", PlannedQuery.Type.ORIGINAL, 1, null),
                new RagRequest("tenant", List.of("ds"), "question", null, policy, Map.of()));
        ArgumentCaptor<RetrievalConfig> config = ArgumentCaptor.forClass(RetrievalConfig.class);
        verify(existing).retrieve(eq(dataset), config.capture(), any());
        assertThat(config.getValue().getScoreThreshold()).isEqualTo(.75);
        assertThat(config.getValue().getTopK()).isEqualTo(42);
        assertThat(config.getValue().getNeighborWindow()).isEqualTo(2);
        assertThat(config.getValue().getMaxChunksPerDocument()).isZero();
        assertThat(config.getValue().isRerankEnabled()).isFalse();
    }
}

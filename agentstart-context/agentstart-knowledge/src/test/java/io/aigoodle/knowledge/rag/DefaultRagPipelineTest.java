package io.aigoodle.knowledge.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aigoodle.knowledge.chunk.TokenCounter;
import io.aigoodle.rag.api.PlannedQuery;
import io.aigoodle.rag.api.QueryPlan;
import io.aigoodle.rag.api.RagPolicy;
import io.aigoodle.rag.api.RagRequest;
import io.aigoodle.rag.api.RetrievalCandidate;
import io.aigoodle.rag.rerank.ScoreReranker;
import io.aigoodle.rag.spi.QueryPlanner;
import io.aigoodle.rag.spi.Retriever;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultRagPipelineTest {
    private final RagPolicy defaults = RagPolicy.defaults();

    @Test
    void appliesTopKWithoutRerankingAndEnforcesDiversityAfterRecall() {
        RagPolicy policy = new RagPolicy(defaults.queryPlanning(),
                new RagPolicy.Retrieval(List.of(RagPolicy.Channel.VECTOR), 30, 4, Map.of(), .3, 1),
                defaults.fusion(), rerank(false, 2), defaults.context(), defaults.budget());
        var result = pipeline(List.of(candidate("s1", "doc1", .9, "q0"), candidate("s2", "doc1", .8, "q0"),
                candidate("s3", "doc2", .7, "q0")), false).retrieve(request(policy));
        assertThat(result.evidence()).extracting(e -> e.segmentId()).containsExactly("s1", "s3");
        assertThat(result.evidence().getFirst().fusionScore()).isNull();
        assertThat(result.evidence().getFirst().rerankScore()).isNull();
    }

    @Test
    void preservesRawScoresAndAllMatchingQueriesAcrossFusionAndReranking() {
        RagPolicy policy = new RagPolicy(new RagPolicy.QueryPlanning(RagPolicy.QueryStrategy.MULTI_QUERY, "model", 2, true),
                defaults.retrieval(), new RagPolicy.Fusion(RagPolicy.FusionMethod.RRF, 60, Map.of()),
                rerank(true, 2), defaults.context(), defaults.budget());
        var result = pipeline(List.of(candidate("shared", "doc", .9, "q0")), true).retrieve(request(policy));
        var evidence = result.evidence().getFirst();
        assertThat(evidence.retrievalScore()).isEqualTo(.9);
        assertThat(evidence.fusionScore()).isLessThan(.9);
        assertThat(evidence.rerankScore()).isEqualTo(evidence.fusionScore());
        assertThat(evidence.matchedQueryIds()).containsExactly("q0", "q1");
    }

    @Test
    void respectsContextBudgetAndParentExpansionFlag() {
        RagPolicy.Context context = new RagPolicy.Context(false, 0, true, false, null, 2);
        RagPolicy policy = new RagPolicy(defaults.queryPlanning(), defaults.retrieval(), defaults.fusion(),
                rerank(false, 5), context, defaults.budget());
        var result = pipeline(List.of(candidate("中文检索测试", "doc", .9, "q0"), candidate("second", "doc2", .8, "q0")), false)
                .retrieve(request(policy));
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().getFirst().content()).isEqualTo("中文检索测试");
        assertThat(result.evidence().getFirst().context()).isEqualTo("中文");
        assertThat(TokenCounter.count(result.evidence().getFirst().context())).isEqualTo(2);
    }

    @Test
    void rejectsUnavailableChannelsInsteadOfReportingAnEmptySuccessfulRun() {
        RagPolicy policy = new RagPolicy(defaults.queryPlanning(),
                new RagPolicy.Retrieval(List.of(RagPolicy.Channel.SPARSE), 30, 4, Map.of()),
                defaults.fusion(), defaults.rerank(), defaults.context(), defaults.budget());
        assertThatThrownBy(() -> pipeline(List.of(), false).retrieve(request(policy)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SPARSE");
    }

    private RagPolicy.Rerank rerank(boolean enabled, int topK) {
        return new RagPolicy.Rerank(enabled, "score", null, 30, topK, defaults.rerank().timeout(), true);
    }

    private RagRequest request(RagPolicy policy) {
        return new RagRequest("tenant", List.of("ds"), "question", null, policy, Map.of());
    }

    private DefaultRagPipeline pipeline(List<RetrievalCandidate> candidates, boolean multi) {
        QueryPlanner planner = new QueryPlanner() {
            public String getName() { return multi ? "MODEL" : "DIRECT"; }
            public QueryPlan plan(RagRequest request) {
                List<PlannedQuery> queries = multi
                        ? List.of(new PlannedQuery("q0", "question", PlannedQuery.Type.ORIGINAL, 1, null),
                                new PlannedQuery("q1", "variant", PlannedQuery.Type.MULTI_QUERY, 1, null))
                        : List.of(new PlannedQuery("q0", "question", PlannedQuery.Type.ORIGINAL, 1, null));
                return new QueryPlan(request.query(), queries);
            }
        };
        Retriever retriever = new Retriever() {
            public String getName() { return "test"; }
            public RagPolicy.Channel getChannel() { return RagPolicy.Channel.VECTOR; }
            public List<RetrievalCandidate> retrieve(PlannedQuery query, RagRequest request) {
                return candidates.stream().map(c -> candidate(c.segmentId(), c.documentId(), c.score(), query.id())).toList();
            }
        };
        return new DefaultRagPipeline(List.of(planner), List.of(retriever), List.of(new ReciprocalRankFusion()),
                List.of(new ScoreReranker()), List.of());
    }

    private static RetrievalCandidate candidate(String id, String document, double score, String query) {
        return new RetrievalCandidate("ds", document, id, 0, id, "Parent context", score,
                RagPolicy.Channel.VECTOR, query, Map.of());
    }
}

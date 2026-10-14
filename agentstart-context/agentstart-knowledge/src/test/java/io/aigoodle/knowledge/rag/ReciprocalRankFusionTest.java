package io.aigoodle.knowledge.rag;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.rag.api.RagPolicy;
import io.aigoodle.rag.api.RetrievalCandidate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {
    @Test
    void rewardsCandidatesReturnedByMultipleQueries() {
        RetrievalCandidate sharedA = candidate("shared", "q1");
        RetrievalCandidate onlyA = candidate("only-a", "q1");
        RetrievalCandidate onlyB = candidate("only-b", "q2");
        RetrievalCandidate sharedB = candidate("shared", "q2");
        var policy = new RagPolicy.Fusion(RagPolicy.FusionMethod.RRF, 60, Map.of());

        var result = new ReciprocalRankFusion().fuse(List.of(List.of(sharedA, onlyA), List.of(onlyB, sharedB)), policy);

        assertThat(result)
                .extracting(RetrievalCandidate::segmentId)
                .startsWith("shared")
                .containsExactlyInAnyOrder("shared", "only-a", "only-b");
    }

    private static RetrievalCandidate candidate(String id, String queryId) {
        return new RetrievalCandidate("ds", "doc", id, 0, id, id, 1, RagPolicy.Channel.VECTOR, queryId, Map.of());
    }
}

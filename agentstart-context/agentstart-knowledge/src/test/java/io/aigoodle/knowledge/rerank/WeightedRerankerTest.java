package io.aigoodle.knowledge.rerank;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.knowledge.retrieve.RetrievedSegment;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeightedRerankerTest {

    @Test
    void combinesVectorSimilarityAndLengthPrior() {
        WeightedReranker reranker = new WeightedReranker(new WeightedRerankerSettings(0.9, 0.1, 400));
        RetrievedSegment vectorMatch = candidate("vector", 1.0);
        RetrievedSegment balancedMatch = candidate("balanced".repeat(50), 0.8);
        RetrievedSegment weakMatch = candidate("weak", 0.1);

        List<RetrievedSegment> results = reranker.rerank("query", List.of(vectorMatch, balancedMatch, weakMatch), 2);

        assertThat(results).extracting(RetrievedSegment::getContent).containsExactly("vector", "balanced".repeat(50));
    }

    private static RetrievedSegment candidate(String content, double vectorScore) {
        return RetrievedSegment.builder()
                .content(content)
                .vectorScore(vectorScore)
                .build();
    }
}

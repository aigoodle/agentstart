package io.aigoodle.knowledge.rerank;

import io.aigoodle.knowledge.retrieve.RetrievedSegment;
import java.util.List;

/**
 * Identity reranker: returns the top-N candidates unchanged. Used when reranking is
 * disabled so callers can rely on a non-null Reranker bean.
 */
public class NoopReranker implements Reranker {

    public static final String NAME = "noop";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public List<RetrievedSegment> rerank(String query, List<RetrievedSegment> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (topN <= 0 || topN >= candidates.size()) {
            return candidates;
        }
        return candidates.subList(0, topN);
    }
}

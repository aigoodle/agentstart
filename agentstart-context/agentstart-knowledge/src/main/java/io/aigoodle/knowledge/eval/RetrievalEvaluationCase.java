package io.aigoodle.knowledge.eval;

import java.util.List;
import java.util.Set;

/** A version-controlled golden query used to measure retrieval quality. */
public record RetrievalEvaluationCase(
        String id,
        String query,
        Set<String> relevantSegmentIds,
        boolean noAnswerExpected,
        List<String> citedSegmentIds) {

    public RetrievalEvaluationCase {
        relevantSegmentIds = relevantSegmentIds == null ? Set.of() : Set.copyOf(relevantSegmentIds);
        citedSegmentIds = citedSegmentIds == null ? List.of() : List.copyOf(citedSegmentIds);
    }
}

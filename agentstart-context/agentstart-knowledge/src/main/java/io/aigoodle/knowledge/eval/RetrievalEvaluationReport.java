package io.aigoodle.knowledge.eval;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Immutable report suitable for JSON persistence or CI artifacts. */
public record RetrievalEvaluationReport(
        String datasetName,
        String datasetVersion,
        String experimentName,
        int topK,
        Instant evaluatedAt,
        RetrievalEvaluationMetrics metrics,
        List<CaseResult> cases,
        Map<String, String> configuration) {

    public record CaseResult(
            String caseId,
            List<String> retrievedSegmentIds,
            double recallAtK,
            double precisionAtK,
            double reciprocalRank,
            double ndcgAtK,
            Boolean rejectedNoAnswer,
            Double citationAccuracy) {}
}

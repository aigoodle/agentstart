package io.aigoodle.knowledge.eval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Runs a golden query set without depending on a particular retriever implementation. */
public class RetrievalEvaluator {

    public RetrievalEvaluationReport evaluate(
            RetrievalEvaluationDataset dataset,
            String experimentName,
            int topK,
            Map<String, String> configuration,
            Function<String, List<String>> retrieval) {
        if (topK <= 0) throw new IllegalArgumentException("topK must be positive");
        List<RetrievalEvaluationReport.CaseResult> results = new ArrayList<>();
        for (RetrievalEvaluationCase testCase : dataset.cases()) {
            List<String> retrieved = retrieval.apply(testCase.query());
            List<String> ranked = retrieved == null
                    ? List.of()
                    : retrieved.stream().limit(topK).toList();
            results.add(score(testCase, ranked, topK));
        }
        return new RetrievalEvaluationReport(
                dataset.name(),
                dataset.version(),
                experimentName,
                topK,
                Instant.now(),
                aggregate(results),
                results,
                configuration == null ? Map.of() : Map.copyOf(configuration));
    }

    private static RetrievalEvaluationReport.CaseResult score(
            RetrievalEvaluationCase testCase, List<String> ranked, int topK) {
        Set<String> relevant = testCase.relevantSegmentIds();
        long hits = ranked.stream().filter(relevant::contains).distinct().count();
        double recall = relevant.isEmpty() ? 0.0 : (double) hits / relevant.size();
        double precision = (double) hits / topK;
        double reciprocalRank = 0.0;
        double dcg = 0.0;
        for (int i = 0; i < ranked.size(); i++) {
            if (relevant.contains(ranked.get(i))) {
                if (reciprocalRank == 0.0) reciprocalRank = 1.0 / (i + 1);
                dcg += 1.0 / log2(i + 2);
            }
        }
        double idcg = 0.0;
        for (int i = 0; i < Math.min(relevant.size(), topK); i++) idcg += 1.0 / log2(i + 2);
        double ndcg = idcg == 0.0 ? 0.0 : dcg / idcg;
        Boolean rejection = testCase.noAnswerExpected() ? ranked.isEmpty() : null;
        Double citationAccuracy = null;
        if (!testCase.citedSegmentIds().isEmpty()) {
            long correct = testCase.citedSegmentIds().stream()
                    .filter(relevant::contains)
                    .count();
            citationAccuracy = (double) correct / testCase.citedSegmentIds().size();
        }
        return new RetrievalEvaluationReport.CaseResult(
                testCase.id(), ranked, recall, precision, reciprocalRank, ndcg, rejection, citationAccuracy);
    }

    private static RetrievalEvaluationMetrics aggregate(List<RetrievalEvaluationReport.CaseResult> results) {
        if (results.isEmpty()) return new RetrievalEvaluationMetrics(0, 0, 0, 0, 0, 0);
        double recall = results.stream()
                .mapToDouble(RetrievalEvaluationReport.CaseResult::recallAtK)
                .average()
                .orElse(0);
        double precision = results.stream()
                .mapToDouble(RetrievalEvaluationReport.CaseResult::precisionAtK)
                .average()
                .orElse(0);
        double mrr = results.stream()
                .mapToDouble(RetrievalEvaluationReport.CaseResult::reciprocalRank)
                .average()
                .orElse(0);
        double ndcg = results.stream()
                .mapToDouble(RetrievalEvaluationReport.CaseResult::ndcgAtK)
                .average()
                .orElse(0);
        double rejection = results.stream()
                .filter(r -> r.rejectedNoAnswer() != null)
                .mapToDouble(r -> Boolean.TRUE.equals(r.rejectedNoAnswer()) ? 1 : 0)
                .average()
                .orElse(0);
        double citations = results.stream()
                .filter(r -> r.citationAccuracy() != null)
                .mapToDouble(RetrievalEvaluationReport.CaseResult::citationAccuracy)
                .average()
                .orElse(0);
        return new RetrievalEvaluationMetrics(recall, precision, mrr, ndcg, rejection, citations);
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2);
    }
}

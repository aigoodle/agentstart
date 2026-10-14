package io.aigoodle.knowledge.eval;

import java.util.ArrayList;
import java.util.List;

/** CI gate comparing a candidate experiment with the last accepted baseline. */
public class RetrievalRegressionGate {

    public GateResult compare(
            RetrievalEvaluationReport baseline, RetrievalEvaluationReport candidate, Thresholds thresholds) {
        List<String> violations = new ArrayList<>();
        compare(
                "recall@k",
                baseline.metrics().recallAtK(),
                candidate.metrics().recallAtK(),
                thresholds.maxRecallDrop(),
                violations);
        compare(
                "precision@k",
                baseline.metrics().precisionAtK(),
                candidate.metrics().precisionAtK(),
                thresholds.maxPrecisionDrop(),
                violations);
        compare("mrr", baseline.metrics().mrr(), candidate.metrics().mrr(), thresholds.maxMrrDrop(), violations);
        compare(
                "nDCG@k",
                baseline.metrics().ndcgAtK(),
                candidate.metrics().ndcgAtK(),
                thresholds.maxNdcgDrop(),
                violations);
        compare(
                "no-answer rejection",
                baseline.metrics().noAnswerRejectionRate(),
                candidate.metrics().noAnswerRejectionRate(),
                thresholds.maxNoAnswerDrop(),
                violations);
        compare(
                "citation accuracy",
                baseline.metrics().citationAccuracy(),
                candidate.metrics().citationAccuracy(),
                thresholds.maxCitationDrop(),
                violations);
        return new GateResult(violations.isEmpty(), List.copyOf(violations));
    }

    private static void compare(
            String metric, double baseline, double candidate, double allowedDrop, List<String> violations) {
        double drop = baseline - candidate;
        if (drop > allowedDrop) {
            violations.add(metric + " regressed by " + drop + " (allowed " + allowedDrop + ")");
        }
    }

    public record Thresholds(
            double maxRecallDrop,
            double maxPrecisionDrop,
            double maxMrrDrop,
            double maxNdcgDrop,
            double maxNoAnswerDrop,
            double maxCitationDrop) {}

    public record GateResult(boolean passed, List<String> violations) {}
}

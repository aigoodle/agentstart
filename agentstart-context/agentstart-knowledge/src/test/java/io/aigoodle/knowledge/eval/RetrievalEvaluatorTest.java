package io.aigoodle.knowledge.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RetrievalEvaluatorTest {

    @Test
    void calculatesRankingRejectionAndCitationMetrics() {
        RetrievalEvaluationDataset dataset = new RetrievalEvaluationDataset(
                "golden",
                "v1",
                List.of(
                        new RetrievalEvaluationCase("q1", "alpha", Set.of("a", "b"), false, List.of("a", "x")),
                        new RetrievalEvaluationCase("q2", "unknown", Set.of(), true, List.of())));

        RetrievalEvaluationReport report = new RetrievalEvaluator()
                .evaluate(
                        dataset,
                        "baseline",
                        2,
                        Map.of("chunker", "naive-v1"),
                        query -> query.equals("alpha") ? List.of("x", "a", "b") : List.of());

        assertThat(report.metrics().recallAtK()).isEqualTo(0.25);
        assertThat(report.metrics().precisionAtK()).isEqualTo(0.25);
        assertThat(report.metrics().mrr()).isEqualTo(0.25);
        assertThat(report.metrics().ndcgAtK()).isBetween(0.19, 0.20);
        assertThat(report.metrics().noAnswerRejectionRate()).isEqualTo(1.0);
        assertThat(report.metrics().citationAccuracy()).isEqualTo(0.5);
    }

    @Test
    void failsGateWhenCandidateExceedsAllowedDrop() {
        RetrievalEvaluationReport baseline = report(new RetrievalEvaluationMetrics(.9, .8, .7, .6, 1, .9));
        RetrievalEvaluationReport candidate = report(new RetrievalEvaluationMetrics(.7, .8, .7, .6, 1, .9));

        RetrievalRegressionGate.GateResult result = new RetrievalRegressionGate()
                .compare(baseline, candidate, new RetrievalRegressionGate.Thresholds(.05, .05, .05, .05, .05, .05));

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).singleElement().asString().contains("recall@k");
    }

    private static RetrievalEvaluationReport report(RetrievalEvaluationMetrics metrics) {
        return new RetrievalEvaluationReport(
                "golden", "v1", "test", 5, java.time.Instant.EPOCH, metrics, List.of(), Map.of());
    }
}

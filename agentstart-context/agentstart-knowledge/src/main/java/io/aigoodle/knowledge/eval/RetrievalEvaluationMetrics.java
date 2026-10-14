package io.aigoodle.knowledge.eval;

/** Macro-averaged retrieval and answer-grounding metrics. */
public record RetrievalEvaluationMetrics(
        double recallAtK,
        double precisionAtK,
        double mrr,
        double ndcgAtK,
        double noAnswerRejectionRate,
        double citationAccuracy) {}

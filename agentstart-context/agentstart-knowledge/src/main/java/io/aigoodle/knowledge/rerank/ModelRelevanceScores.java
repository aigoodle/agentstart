package io.aigoodle.knowledge.rerank;

import java.util.Arrays;
import java.util.OptionalDouble;

/** Parsed model scores, preserving which candidate indexes were omitted. */
final class ModelRelevanceScores {

    private final double[] scores;

    ModelRelevanceScores(int candidateCount) {
        this.scores = new double[candidateCount];
        Arrays.fill(scores, Double.NaN);
    }

    void record(int candidateIndex, double score) {
        if (candidateIndex >= 0 && candidateIndex < scores.length) {
            scores[candidateIndex] = Math.clamp(score, 0.0, 1.0);
        }
    }

    OptionalDouble scoreAt(int candidateIndex) {
        double score = scores[candidateIndex];
        return Double.isNaN(score) ? OptionalDouble.empty() : OptionalDouble.of(score);
    }
}

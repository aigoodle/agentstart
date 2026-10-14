package io.aigoodle.knowledge.rerank;

/** Normalized vector scoring and content-length preference for weighted reranking. */
public record WeightedRerankerSettings(double vectorWeight, double lengthWeight, int idealContentLength) {

    private static final int MINIMUM_IDEAL_CONTENT_LENGTH = 50;

    public WeightedRerankerSettings {
        requireNonNegativeFinite("vectorWeight", vectorWeight);
        requireNonNegativeFinite("lengthWeight", lengthWeight);
        double totalWeight = vectorWeight + lengthWeight;
        if (totalWeight > 0) {
            vectorWeight /= totalWeight;
            lengthWeight /= totalWeight;
        }
        idealContentLength = Math.max(MINIMUM_IDEAL_CONTENT_LENGTH, idealContentLength);
    }

    public static WeightedRerankerSettings defaults() {
        return new WeightedRerankerSettings(0.9, 0.1, 400);
    }

    private static void requireNonNegativeFinite(String name, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be a non-negative finite number");
        }
    }
}

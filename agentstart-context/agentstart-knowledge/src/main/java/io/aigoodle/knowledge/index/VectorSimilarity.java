package io.aigoodle.knowledge.index;

/** Pure vector similarity functions used by portable stores. */
final class VectorSimilarity {

    private VectorSimilarity() {}

    static double cosine(float[] left, float[] right) {
        if (left.length == 0 || right.length != left.length) {
            return 0.0;
        }

        double dotProduct = 0;
        double leftMagnitudeSquared = 0;
        double rightMagnitudeSquared = 0;
        for (int index = 0; index < left.length; index++) {
            dotProduct += left[index] * right[index];
            leftMagnitudeSquared += left[index] * left[index];
            rightMagnitudeSquared += right[index] * right[index];
        }
        if (leftMagnitudeSquared == 0 || rightMagnitudeSquared == 0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(leftMagnitudeSquared) * Math.sqrt(rightMagnitudeSquared));
    }
}

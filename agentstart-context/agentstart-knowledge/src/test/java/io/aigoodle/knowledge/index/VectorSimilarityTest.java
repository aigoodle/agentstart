package io.aigoodle.knowledge.index;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VectorSimilarityTest {

    @Test
    void computesCosineSimilarityUsingNamedOperands() {
        assertThat(VectorSimilarity.cosine(new float[] {1, 0}, new float[] {1, 0}))
                .isEqualTo(1.0);
        assertThat(VectorSimilarity.cosine(new float[] {1, 0}, new float[] {0, 1}))
                .isEqualTo(0.0);
    }

    @Test
    void returnsZeroForIncompatibleOrZeroVectors() {
        assertThat(VectorSimilarity.cosine(new float[0], new float[0])).isZero();
        assertThat(VectorSimilarity.cosine(new float[] {1}, new float[] {1, 2})).isZero();
        assertThat(VectorSimilarity.cosine(new float[] {0, 0}, new float[] {1, 1}))
                .isZero();
    }
}

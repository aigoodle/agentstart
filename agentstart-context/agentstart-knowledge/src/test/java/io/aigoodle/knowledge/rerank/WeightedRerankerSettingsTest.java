package io.aigoodle.knowledge.rerank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class WeightedRerankerSettingsTest {

    @Test
    void normalizesWeightsAndAppliesTheMinimumIdealLength() {
        WeightedRerankerSettings settings = new WeightedRerankerSettings(9, 1, 10);

        assertThat(settings.vectorWeight()).isEqualTo(0.9);
        assertThat(settings.lengthWeight()).isEqualTo(0.1);
        assertThat(settings.idealContentLength()).isEqualTo(50);
    }

    @Test
    void rejectsWeightsThatCannotRepresentAValidContribution() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WeightedRerankerSettings(-1, 1, 400))
                .withMessageContaining("vectorWeight");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WeightedRerankerSettings(Double.NaN, 1, 400))
                .withMessageContaining("vectorWeight");
    }
}

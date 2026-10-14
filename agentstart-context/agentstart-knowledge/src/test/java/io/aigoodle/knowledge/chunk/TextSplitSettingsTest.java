package io.aigoodle.knowledge.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextSplitSettingsTest {

    @Test
    void normalizesTokenBudgetsAndTakesASeparatorSnapshot() {
        List<String> separators = new ArrayList<>(List.of("\n", "."));

        TextSplitSettings settings = new TextSplitSettings(separators, 0, 100);
        separators.clear();

        assertThat(settings.maximumTokens()).isEqualTo(1);
        assertThat(settings.overlapTokens()).isEqualTo(1);
        assertThat(settings.separators()).containsExactly("\n", ".");
    }

    @Test
    void namedFactoryMakesAParentSplitWithoutOverlap() {
        TextSplitSettings settings = TextSplitSettings.withoutOverlap(List.of("\n"), 200);

        assertThat(settings.maximumTokens()).isEqualTo(200);
        assertThat(settings.overlapTokens()).isZero();
    }
}

package io.aigoodle.knowledge.rerank;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelRelevanceScoreParserTest {

    @Test
    void parsesSupportedLineFormatsAndClampsScores() {
        ModelRelevanceScores scores = ModelRelevanceScoreParser.parse("[0]: 0.75\n1 = 1.4\n2) 0.1\n7: 0.9", 3);

        assertThat(scores.scoreAt(0)).hasValue(0.75);
        assertThat(scores.scoreAt(1)).hasValue(1.0);
        assertThat(scores.scoreAt(2)).hasValue(0.1);
    }

    @Test
    void preservesMissingScoresInsteadOfInventingAValue() {
        ModelRelevanceScores scores = ModelRelevanceScoreParser.parse("0: 0.8", 2);

        assertThat(scores.scoreAt(0)).hasValue(0.8);
        assertThat(scores.scoreAt(1)).isEmpty();
    }
}

package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConditionMatcherTest {

    @Test
    void comparesNumericStringsNumerically() {
        assertThat(ConditionMatcher.matches("greater_than", "10", "2", "2")).isTrue();
        assertThat(ConditionMatcher.matches("less_than", "10", "2", "2")).isFalse();
    }

    @Test
    void acceptsDesignerListValuesForInOperator() {
        List<String> expectedValues = List.of("greet", "chat");

        assertThat(ConditionMatcher.matches("in", "chat", String.valueOf(expectedValues), expectedValues))
                .isTrue();
    }

    @Test
    void coercesCommonBooleanRepresentations() {
        assertThat(ConditionMatcher.matches("is_true", 1, "", null)).isTrue();
        assertThat(ConditionMatcher.matches("is_false", "off", "", null)).isTrue();
    }

    @Test
    void invalidRegexIsARegularNonMatch() {
        assertThat(ConditionMatcher.matches("regex", "content", "[", "[")).isFalse();
    }
}

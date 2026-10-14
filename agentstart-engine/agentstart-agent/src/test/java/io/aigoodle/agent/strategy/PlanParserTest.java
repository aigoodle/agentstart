package io.aigoodle.agent.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlanParserTest {

    private final PlanParser parser = new PlanParser();

    @Test
    void extractsJsonPlanFromModelCommentary() {
        assertThat(parser.parse("Here is the plan: [\"research\", \"summarize\"]", "fallback"))
                .containsExactly("research", "summarize");
    }

    @Test
    void usesOriginalTaskWhenPlanIsEmpty() {
        assertThat(parser.parse("[]", "answer the question")).containsExactly("answer the question");
    }

    @Test
    void usesOriginalTaskWhenPlanJsonIsInvalid() {
        assertThat(parser.parse("[not valid json]", "answer safely")).containsExactly("answer safely");
    }
}

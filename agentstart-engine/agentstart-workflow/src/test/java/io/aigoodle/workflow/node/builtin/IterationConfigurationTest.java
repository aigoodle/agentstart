package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import org.junit.jupiter.api.Test;

class IterationConfigurationTest {

    @Test
    void usesReadableDefaultVariableNames() {
        IterationConfiguration configuration = IterationConfiguration.from(iterationNode());

        assertThat(configuration.itemVariable()).isEqualTo("item");
        assertThat(configuration.indexVariable()).isEqualTo("index");
        assertThat(configuration.outputVariable()).isEqualTo("output");
        assertThat(configuration.continueOnError()).isFalse();
        assertThat(configuration.inputsFor("Alice", 2))
                .containsEntry("item", "Alice")
                .containsEntry("index", 2)
                .hasSize(2);
    }

    @Test
    void normalizesCustomVariableNames() {
        NodeDef node = iterationNode()
                .with("itemKey", " customer ")
                .with("indexKey", " position ")
                .with("outputKey", " results ")
                .with("continueOnError", "true");

        IterationConfiguration configuration = IterationConfiguration.from(node);

        assertThat(configuration.itemVariable()).isEqualTo("customer");
        assertThat(configuration.indexVariable()).isEqualTo("position");
        assertThat(configuration.outputVariable()).isEqualTo("results");
        assertThat(configuration.continueOnError()).isTrue();
    }

    @Test
    void blankVariableNamesFallBackToDefaults() {
        NodeDef node = iterationNode().with("itemKey", " ").with("indexKey", "").with("outputKey", "  ");

        IterationConfiguration configuration = IterationConfiguration.from(node);

        assertThat(configuration.itemVariable()).isEqualTo("item");
        assertThat(configuration.indexVariable()).isEqualTo("index");
        assertThat(configuration.outputVariable()).isEqualTo("output");
    }

    private static NodeDef iterationNode() {
        return NodeDef.of("iteration", NodeType.ITERATION);
    }
}

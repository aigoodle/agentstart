package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.workflow.node.NodeResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmStructuredOutputMapperTest {

    @Test
    void exposesJsonObjectAsWholeAndAddressableFields() {
        NodeResult result = LlmStructuredOutputMapper.map("{\"intent\":\"greeting\",\"confidence\":0.95}");

        assertThat(result.getOutputs().get("text")).isEqualTo("{\"intent\":\"greeting\",\"confidence\":0.95}");
        assertThat(result.getOutputs().get("struct")).isEqualTo(Map.of("intent", "greeting", "confidence", 0.95));
        assertThat(result.getOutputs().get("struct.intent")).isEqualTo("greeting");
        assertThat(result.getOutputs().get("struct.confidence")).isEqualTo(0.95);
    }

    @Test
    void preservesPlainTextWhenProviderIgnoresStructuredMode() {
        NodeResult result = LlmStructuredOutputMapper.map("The answer is 42");

        assertThat(result.getOutputs()).isEqualTo(Map.of("text", "The answer is 42"));
    }

    @Test
    void preservesEmptyResponseWithoutSyntheticFields() {
        NodeResult result = LlmStructuredOutputMapper.map(null);

        assertThat(result.getOutputs()).containsEntry("text", null);
        assertThat(result.getOutputs()).doesNotContainKey("struct");
    }
}

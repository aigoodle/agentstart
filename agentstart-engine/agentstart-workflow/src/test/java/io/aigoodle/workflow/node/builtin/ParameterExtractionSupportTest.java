package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParameterExtractionSupportTest {

    @Test
    void normalizesDesignerRowsIntoAReadableSchema() {
        ExtractionParameterSet parameters = ExtractionParameterSet.from(List.of(
                Map.of("name", "city", "type", "string", "description", "Destination city"),
                Map.of("name", "nights", "type", "integer"),
                Map.of("name", "  ", "type", "string")));

        assertThat(parameters.isEmpty()).isFalse();
        assertThat(parameters.schemaInstruction())
                .contains("\"city\": \"string\"  // Destination city")
                .contains("\"nights\": \"integer\"")
                .doesNotContain("\"  \"");
    }

    @Test
    void combinesAndRendersTheDesignerSystemPrompt() {
        ExecutionContext context = new ExecutionContext();
        context.getPool().setSystem("locale", "Chinese");
        NodeDef node = NodeDef.of("extract", NodeType.PARAMETER_EXTRACTOR)
                .with(
                        "systemPrompt",
                        Map.of(
                                "id", "prompt-1",
                                "role", "system",
                                "text", "Read the input as {{#sys.locale#}}."));
        ExtractionParameterSet parameters = ExtractionParameterSet.from(List.of(Map.of("name", "city")));

        String prompt = ParameterExtractionPromptBuilder.build(node, context, parameters);

        assertThat(prompt).startsWith("Read the input as Chinese.\n\n").contains("\"city\": \"string\"");
    }

    @Test
    void parsesPlainAndMarkdownWrappedJsonResponses() {
        assertThat(JsonObjectResponseParser.parse(" {\"city\":\"Shanghai\"} ")).containsEntry("city", "Shanghai");
        assertThat(
                        JsonObjectResponseParser.parse(
                                """
                ```json
                {"city":"Singapore","nights":3}
                ```
                """))
                .containsEntry("city", "Singapore")
                .containsEntry("nights", 3);
    }

    @Test
    void writesEveryDeclaredOutputEvenWhenExtractionFails() {
        ExtractionParameterSet parameters =
                ExtractionParameterSet.from(List.of(Map.of("name", "city"), Map.of("name", "nights")));
        NodeResult result = NodeResult.empty();

        parameters.writeMissingValues(result);

        assertThat(result.getOutputs()).containsKeys("city", "nights");
        assertThat(result.getOutputs().get("city")).isNull();
    }

    @Test
    void mapsModelResponseToEveryDeclaredWorkflowOutput() {
        ExtractionParameterSet parameters = ExtractionParameterSet.from(
                List.of(Map.of("name", "city"), Map.of("name", "nights"), Map.of("name", "missing")));

        NodeResult result = parameters.resultFrom("{\"city\":\"Singapore\",\"nights\":3}");

        assertThat(result.getOutputs())
                .containsEntry("city", "Singapore")
                .containsEntry("nights", 3)
                .containsKey("missing")
                .containsEntry("result", "{\"city\":\"Singapore\",\"nights\":3}");
        assertThat(result.getOutputs().get("missing")).isNull();
    }

    @Test
    void failedExtractionKeepsFieldsAddressableAndProvidesReadableError() {
        ExtractionParameterSet parameters = ExtractionParameterSet.from(List.of(Map.of("name", "city")));

        NodeResult result = parameters.failedResult(new IllegalStateException());

        assertThat(result.getOutputs()).containsKeys("city", "error");
        assertThat(result.getOutputs().get("city")).isNull();
        assertThat(result.getOutputs().get("error")).isEqualTo("IllegalStateException");
    }
}

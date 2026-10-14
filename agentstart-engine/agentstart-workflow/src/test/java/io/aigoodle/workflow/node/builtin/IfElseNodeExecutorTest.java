package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IfElseNodeExecutorTest {

    private final IfElseNodeExecutor executor = new IfElseNodeExecutor();
    private final ExecutionContext context = ExecutionContext.start(Map.of("intent", "support"), null, null);

    @Test
    void selectsFirstMatchingDesignerCase() {
        NodeDef node = ifElseNode()
                .with(
                        "cases",
                        List.of(
                                designerCase("billing", "Billing", "billing"),
                                designerCase("support", "Support", "support"),
                                designerCase("fallback-match", "Fallback match", "support")));

        NodeResult result = executor.execute(node, context);

        assertThat(result.getHandle()).isEqualTo("support");
        assertThat(result.getOutputs())
                .containsEntry("case", "Support")
                .containsEntry("caseIndex", 1)
                .containsEntry("result", true);
    }

    @Test
    void usesImplicitElseWhenNoDesignerCaseMatches() {
        NodeDef node = ifElseNode().with("cases", List.of(designerCase("billing", "Billing", "billing")));

        NodeResult result = executor.execute(node, context);

        assertThat(result.getHandle()).isEqualTo("false");
        assertThat(result.getOutputs())
                .containsEntry("case", "false")
                .containsEntry("caseIndex", -1)
                .containsEntry("result", false);
    }

    @Test
    void preservesLegacyFlatTrueFalseBranch() {
        NodeDef node = ifElseNode()
                .with("logicalOperator", "and")
                .with("conditions", List.of(Map.of("variable", "sys.intent", "operator", "is", "value", "support")));

        NodeResult result = executor.execute(node, context);

        assertThat(result.getHandle()).isEqualTo("true");
        assertThat(result.getOutputs()).containsEntry("case", "true").containsEntry("result", true);
    }

    private static Map<String, Object> designerCase(String id, String caseLabel, String expectedIntent) {
        return Map.of(
                "id",
                id,
                "caseId",
                caseLabel,
                "logicalOperator",
                "and",
                "conditions",
                List.of(Map.of(
                        "variableSelector", List.of("sys", "intent"), "operator", "is", "value", expectedIntent)));
    }

    private static NodeDef ifElseNode() {
        return NodeDef.of("route", NodeType.IF_ELSE);
    }
}

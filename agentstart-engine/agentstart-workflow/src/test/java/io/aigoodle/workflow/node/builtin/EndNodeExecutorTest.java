package io.aigoodle.workflow.node.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EndNodeExecutorTest {

    @Test
    void resolvesDesignerOutputListFromVariableSelector() {
        ExecutionContext context = ExecutionContext.start(Map.of(), null, null);
        Map<String, Object> structured = Map.of("items", List.of("one", "two"));
        context.getPool().put("llm", "text", "介绍内容");
        context.getPool().put("llm", "structured", structured);
        NodeDef end = NodeDef.of("end", NodeType.END)
                .with(
                        "output",
                        List.of(
                                Map.of(
                                        "name",
                                        "introduction",
                                        "type",
                                        "string",
                                        "variableSelector",
                                        List.of("llm", "text")),
                                Map.of(
                                        "name",
                                        "details",
                                        "type",
                                        "object",
                                        "variableSelector",
                                        List.of("llm", "structured"))));

        NodeResult result = new EndNodeExecutor().execute(end, context);

        assertEquals("介绍内容", result.getOutputs().get("introduction"));
        assertEquals(structured, result.getOutputs().get("details"));
    }

    @Test
    void keepsLegacyOutputsMapCompatible() {
        ExecutionContext context = ExecutionContext.start(Map.of(), null, null);
        context.getPool().put("llm", "text", "hello");
        NodeDef end = NodeDef.of("end", NodeType.END).with("outputs", Map.of("answer", "Result: {{#llm.text#}}"));

        NodeResult result = new EndNodeExecutor().execute(end, context);

        assertEquals("Result: hello", result.getOutputs().get("answer"));
    }
}

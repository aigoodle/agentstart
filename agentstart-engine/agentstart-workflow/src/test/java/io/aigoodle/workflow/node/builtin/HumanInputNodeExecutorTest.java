package io.aigoodle.workflow.node.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HumanInputNodeExecutorTest {

    @Test
    void rendersVariablesThroughoutFormSchema() {
        NodeDef node = NodeDef.of("human", NodeType.HUMAN_INPUT)
                .with(
                        "inputSchema",
                        Map.of(
                                "type", "object",
                                "title", "确认 {{#var.name#}} 的信息",
                                "description", "订单：{{#upstream.orderId#}}\u200B",
                                "properties",
                                        Map.of(
                                                "decision",
                                                Map.of(
                                                        "type",
                                                        "string",
                                                        "enum",
                                                        List.of("{{#var.option#}}", "cancel")))));
        ExecutionContext context =
                ExecutionContext.start(Map.of("name", "张三", "option", "confirm"), "conversation", null);
        context.getPool().put("upstream", "orderId", "A-100");

        NodeResult result = new HumanInputNodeExecutor().execute(node, context);
        Map<String, Object> schema = result.getWaitRequest().inputSchema();

        assertEquals("确认 张三 的信息", schema.get("title"));
        assertEquals("订单：A-100", schema.get("description"));
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> decision = (Map<String, Object>) properties.get("decision");
        assertEquals(List.of("confirm", "cancel"), decision.get("enum"));
    }
}

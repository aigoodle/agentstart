package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import io.aigoodle.workflow.variable.VariableResolver;
import java.util.List;
import java.util.Map;

/**
 * Produces the workflow's final outputs. Supports both the engine's legacy
 * {@code outputs} map and the visual designer's {@code output} list containing
 * {@code name} and {@code variableSelector} fields.
 */
public class EndNodeExecutor implements NodeExecutor {

    @Override
    public NodeType type() {
        return NodeType.END;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        NodeResult result = NodeResult.empty();
        Object configuredOutputs = node.get("outputs");
        if (configuredOutputs instanceof Map<?, ?> outputs) {
            for (Map.Entry<?, ?> output : outputs.entrySet()) {
                String outputName = String.valueOf(output.getKey());
                String outputTemplate = String.valueOf(output.getValue());
                result.output(outputName, VariableResolver.render(outputTemplate, context.getPool()));
            }
        }
        for (Map<String, Object> output : node.getMapList("output")) {
            Object configuredName = output.get("name");
            if (configuredName == null || String.valueOf(configuredName).isBlank()) {
                continue;
            }
            Object value = resolveDesignerOutput(output, context);
            result.output(String.valueOf(configuredName), value);
        }
        return result;
    }

    private static Object resolveDesignerOutput(Map<String, Object> output, ExecutionContext context) {
        Object selector = output.get("variableSelector");
        if (selector == null) {
            selector = output.get("variable_selector");
        }
        if (selector instanceof List<?> parts && !parts.isEmpty()) {
            String path = parts.stream()
                    .map(String::valueOf)
                    .reduce((left, right) -> left + "." + right)
                    .orElse("");
            return context.getPool().get(path);
        }
        Object configuredValue = output.get("value");
        return configuredValue instanceof String template
                ? VariableResolver.render(template, context.getPool())
                : configuredValue;
    }
}

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
 * Writes a batch of named values into the variable pool. Config: {@code assignments}
 * — a list of {@code {name, value}} entries. Values are templated against the pool, so
 * assignments can copy or transform earlier node outputs. The values are also emitted
 * as this node's outputs, so downstream nodes can reference them by node id.
 */
public class VariableAssignerNodeExecutor implements NodeExecutor {

    @Override
    public NodeType type() {
        return NodeType.VARIABLE_ASSIGNER;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        List<Map<String, Object>> assignments = node.getMapList("assignments");
        NodeResult result = NodeResult.empty();
        for (Map<String, Object> assignment : assignments) {
            Object configuredName = assignment.get("name");
            if (configuredName == null) {
                continue;
            }
            String variableName = String.valueOf(configuredName);
            Object configuredValue = assignment.get("value");
            Object resolvedValue = configuredValue instanceof String template
                    ? VariableResolver.render(template, context.getPool())
                    : configuredValue;
            result.output(variableName, resolvedValue);
        }
        return result;
    }
}

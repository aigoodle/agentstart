package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import java.util.List;

/**
 * Outputs the first non-null value among a list of variable paths — used to merge
 * the results of converging branches. Config: {@code variables} = list of paths.
 */
public class VariableAggregatorNodeExecutor implements NodeExecutor {

    @Override
    public NodeType type() {
        return NodeType.VARIABLE_AGGREGATOR;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        Object configuredVariables = node.get("variables");
        if (configuredVariables instanceof List<?> variablePaths) {
            for (Object variablePath : variablePaths) {
                Object candidateValue = context.getPool().get(String.valueOf(variablePath));
                if (candidateValue != null && !String.valueOf(candidateValue).isBlank()) {
                    return NodeResult.of("output", candidateValue);
                }
            }
        }
        return NodeResult.of("output", null);
    }
}

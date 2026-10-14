package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;

/**
 * Seeds the run's inputs as this node's outputs so downstream nodes can reference
 * them via {@code {{#start.field#}}}.
 */
public class StartNodeExecutor implements NodeExecutor {

    @Override
    public NodeType type() {
        return NodeType.START;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        NodeResult result = NodeResult.empty();
        context.getInputs().forEach(result::output);
        return result;
    }
}

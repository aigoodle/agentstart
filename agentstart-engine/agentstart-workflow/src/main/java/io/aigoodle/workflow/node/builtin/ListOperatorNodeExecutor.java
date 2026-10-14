package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import java.util.List;

/** Applies the configured transformation to a list from the workflow variable pool. */
public class ListOperatorNodeExecutor implements NodeExecutor {

    @Override
    public NodeType type() {
        return NodeType.LIST_OPERATOR;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        String inputReference = node.getString("inputList");
        Object inputValue = inputReference == null ? null : context.getPool().get(inputReference);
        if (!(inputValue instanceof List<?> inputItems)) {
            return NodeResult.of("result", List.of());
        }

        ListOperationConfiguration operation = ListOperationConfiguration.from(node);
        return NodeResult.of("result", operation.apply(inputItems));
    }
}

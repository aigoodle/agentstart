package io.aigoodle.workflow.node;

import io.aigoodle.workflow.graph.NodeDef;

@FunctionalInterface
public interface NodeCompensationHandler {
    NodeResult compensate(NodeDef node, ExecutionContext context, NodeResult originalResult);
}

package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeType;

public final class SleepUntilNodeExecutor extends AbstractWaitNodeExecutor {
    @Override
    public NodeType type() {
        return NodeType.SLEEP_UNTIL;
    }
}

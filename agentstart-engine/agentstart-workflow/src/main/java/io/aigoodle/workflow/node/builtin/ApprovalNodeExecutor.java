package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.NodeResult;
import java.util.Map;

public final class ApprovalNodeExecutor extends AbstractWaitNodeExecutor {
    @Override
    public NodeType type() {
        return NodeType.APPROVAL;
    }

    @Override
    protected NodeResult resumed(NodeDef node, Object payload) {
        if (!(payload instanceof Map<?, ?> map) || !(map.get("approved") instanceof Boolean)) {
            return NodeResult.failure("APPROVAL resume payload requires boolean 'approved'");
        }
        return super.resumed(node, payload);
    }
}

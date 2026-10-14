package io.aigoodle.workflow.node.builtin;

import io.aigoodle.connector.execution.ConnectorExecutionGateway;
import io.aigoodle.workflow.graph.*;
import io.aigoodle.workflow.node.*;
import java.util.*;

/** Product-facing video node backed by the installed generic video plugin. */
public final class VideoGenerationNodeExecutor extends ConnectorNodeExecutor {
    public VideoGenerationNodeExecutor(ConnectorExecutionGateway gateway) {
        super(gateway);
    }

    @Override
    public NodeType type() {
        return NodeType.VIDEO_GENERATION;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        return super.execute(connector(node), context);
    }

    @Override
    public void cancelWaiting(NodeDef node, Map<String, Object> saved) {
        super.cancelWaiting(connector(node), saved);
    }

    private NodeDef connector(NodeDef node) {
        var copy = NodeDef.of(node.getId(), NodeType.VIDEO_GENERATION);
        copy.setData(new HashMap<>(node.getData()));
        copy.with("provider", "plugin")
                .with("connectorId", "media.video")
                .with("actionId", "generate")
                .with("connectorMode", "CONNECTOR_ACTION");
        copy.with(
                "inputs",
                Map.of(
                        "model",
                        Objects.requireNonNullElse(node.get("model"), Map.of()),
                        "prompt",
                        node.getString("prompt", ""),
                        "parameters",
                        Objects.requireNonNullElse(node.get("parameters"), Map.of())));
        return copy;
    }
}

package io.aigoodle.workflow.engine;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.NodeExecutor;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Maps {@link NodeType} to its {@link NodeExecutor}. Custom executor beans override
 * built-ins for the same type, and new types are picked up automatically.
 */
public class NodeExecutorRegistry {

    private final Map<NodeType, NodeExecutor> executors = new EnumMap<>(NodeType.class);

    public NodeExecutorRegistry(List<NodeExecutor> nodeExecutors) {
        if (nodeExecutors == null) {
            return;
        }
        for (NodeExecutor nodeExecutor : nodeExecutors) {
            executors.put(nodeExecutor.type(), nodeExecutor);
        }
    }

    public NodeExecutor get(NodeType type) {
        NodeExecutor executor = executors.get(type);
        if (executor == null) {
            throw new PlatformException(
                    "no_executor",
                    "No executor registered for node type " + type + " (registered: " + executors.keySet() + ")",
                    null);
        }
        return executor;
    }

    public boolean has(NodeType type) {
        return executors.containsKey(type);
    }
}

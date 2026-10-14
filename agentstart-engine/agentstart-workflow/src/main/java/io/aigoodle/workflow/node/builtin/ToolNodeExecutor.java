package io.aigoodle.workflow.node.builtin;

import io.aigoodle.tool.ToolRegistry;
import io.aigoodle.tool.execution.ToolExecutionContext;
import io.aigoodle.tool.execution.ToolExecutionGateway;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutionMode;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import io.aigoodle.workflow.variable.VariableResolver;
import java.util.HashMap;
import java.util.Map;

/**
 * Directly invokes a registered tool by name (no LLM in the loop). Config:
 * {@code tool} (name), {@code args} (map whose string values are templated),
 * {@code outputKey} (default {@code result}). Available only when the tools module is
 * on the classpath.
 */
public class ToolNodeExecutor implements NodeExecutor {

    private final ToolRegistry toolRegistry;
    private final ToolExecutionGateway executionGateway;

    public ToolNodeExecutor(ToolRegistry toolRegistry) {
        this(toolRegistry, ToolExecutionGateway.direct());
    }

    public ToolNodeExecutor(ToolRegistry toolRegistry, ToolExecutionGateway executionGateway) {
        this.toolRegistry = toolRegistry;
        this.executionGateway = executionGateway;
    }

    @Override
    public NodeType type() {
        return NodeType.TOOL;
    }

    @Override
    public NodeExecutionMode executionMode(NodeDef node) {
        return NodeExecutionMode.SIDE_EFFECT;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        context.throwIfCancelled();
        String toolName = node.getString("tool");
        if (toolName == null) {
            return NodeResult.failure("Tool node requires a 'tool' name");
        }
        Map<String, Object> resolvedArguments = new HashMap<>();
        Object configuredArguments = node.get("args");
        if (configuredArguments instanceof Map<?, ?> arguments) {
            for (Map.Entry<?, ?> argument : arguments.entrySet()) {
                Object configuredValue = argument.getValue();
                Object resolvedValue = configuredValue instanceof String template
                        ? VariableResolver.render(template, context.getPool())
                        : configuredValue;
                resolvedArguments.put(String.valueOf(argument.getKey()), resolvedValue);
            }
        }
        Object toolResult = executionGateway.execute(
                toolRegistry.get(toolName),
                resolvedArguments,
                new ToolExecutionContext(
                        context.getRunId(),
                        context.getTenantId(),
                        node.getId(),
                        context.getConversationId(),
                        Map.of("nodeType", "TOOL", "idempotencyKey", context.getRunId() + ":" + node.getId())));
        context.throwIfCancelled();
        return NodeResult.of(node.getString("outputKey", "result"), toolResult);
    }
}

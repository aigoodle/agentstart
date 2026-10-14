package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutionMode;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import io.aigoodle.workflow.node.WorkflowWaitRequest;
import io.aigoodle.workflow.variable.VariableResolver;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

abstract class AbstractWaitNodeExecutor implements NodeExecutor {

    @Override
    public NodeExecutionMode executionMode(NodeDef node) {
        return NodeExecutionMode.IDEMPOTENT;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        Object resumed = context.getPool().namespace(node.getId()).get("_resume");
        if (resumed != null) return resumed(node, resumed);
        WorkflowWaitRequest request = new WorkflowWaitRequest(
                type(),
                correlation(node, context),
                schema(node, context),
                expiresAt(node),
                wakeAt(node),
                UUID.randomUUID().toString());
        return NodeResult.waiting(request);
    }

    protected NodeResult resumed(NodeDef node, Object payload) {
        NodeResult result = NodeResult.empty();
        if (payload instanceof Map<?, ?> map) {
            map.forEach((key, value) -> result.output(String.valueOf(key), value));
        } else {
            result.output(node.getString("outputKey", "value"), payload);
        }
        return result;
    }

    protected static String correlation(NodeDef node, ExecutionContext context) {
        String configured = node.getString("correlationKey");
        return configured == null || configured.isBlank()
                ? context.getRunId() + ":" + node.getId()
                : VariableResolver.render(configured, context.getPool());
    }

    protected static Map<String, Object> schema(NodeDef node, ExecutionContext context) {
        Object value = node.get("inputSchema");
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> rendered = (Map<String, Object>) renderSchemaValue(map, context);
        return rendered;
    }

    /** Render every string in the JSON Schema while preserving its map/list shape. */
    private static Object renderSchemaValue(Object value, ExecutionContext context) {
        if (value instanceof String text) {
            return VariableResolver.render(text.replace("\u200B", ""), context.getPool());
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> rendered = new LinkedHashMap<>();
            map.forEach((key, item) -> rendered.put(String.valueOf(key), renderSchemaValue(item, context)));
            return rendered;
        }
        if (value instanceof List<?> list) {
            List<Object> rendered = new ArrayList<>(list.size());
            list.forEach(item -> rendered.add(renderSchemaValue(item, context)));
            return rendered;
        }
        return value;
    }

    protected static Instant expiresAt(NodeDef node) {
        int seconds = node.getInt("timeoutSeconds", 0);
        return seconds > 0 ? Instant.now().plusSeconds(seconds) : null;
    }

    private static Instant wakeAt(NodeDef node) {
        if (node.getType() != NodeType.SLEEP_UNTIL) return null;
        String configured = node.getString("until");
        if (configured != null) {
            try {
                return Instant.parse(configured);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("SLEEP_UNTIL requires ISO-8601 'until'", exception);
            }
        }
        int delayMillis = node.getInt("delayMillis", -1);
        if (delayMillis > 0) return Instant.now().plusMillis(delayMillis);
        throw new IllegalArgumentException("SLEEP_UNTIL requires 'until' or positive 'delayMillis'");
    }
}

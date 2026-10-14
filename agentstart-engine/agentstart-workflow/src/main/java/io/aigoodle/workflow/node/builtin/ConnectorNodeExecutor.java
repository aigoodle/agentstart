package io.aigoodle.workflow.node.builtin;

import io.aigoodle.channel.ChannelConnectionService;
import io.aigoodle.channel.ChannelOutboundMessage;
import io.aigoodle.channel.ChannelRuntimeRegistry;
import io.aigoodle.channel.ChannelSendResult;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.connector.ConnectorKey;
import io.aigoodle.connector.execution.ConnectorExecutionContext;
import io.aigoodle.connector.execution.ConnectorExecutionGateway;
import io.aigoodle.connector.execution.ConnectorExecutionRequest;
import io.aigoodle.connector.execution.ConnectorResult;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutionMode;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import io.aigoodle.workflow.node.WorkflowWaitRequest;
import io.aigoodle.workflow.variable.VariableResolver;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Provider-neutral connector node; a connector provider is transport infrastructure, not a workflow type. */
public class ConnectorNodeExecutor implements NodeExecutor {
    private final ConnectorExecutionGateway gateway;
    private final ChannelConnectionService channelConnections;
    private final ChannelRuntimeRegistry channelRuntimes;

    public ConnectorNodeExecutor(ConnectorExecutionGateway gateway) {
        this(gateway, null, null);
    }

    public ConnectorNodeExecutor(
            ConnectorExecutionGateway gateway,
            ChannelConnectionService channelConnections,
            ChannelRuntimeRegistry channelRuntimes) {
        this.gateway = gateway;
        this.channelConnections = channelConnections;
        this.channelRuntimes = channelRuntimes;
    }

    @Override
    public NodeType type() {
        return NodeType.CONNECTOR;
    }

    @Override
    public NodeExecutionMode executionMode(NodeDef node) {
        return NodeExecutionMode.IDEMPOTENT;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        context.throwIfCancelled();
        if ("CHANNEL_MESSAGE".equalsIgnoreCase(node.getString("connectorMode"))) {
            return sendChannelMessage(node, context);
        }
        String provider = required(node, "provider");
        String connectorId = required(node, "connectorId");
        String actionId = required(node, "actionId");
        Map<String, Object> arguments = new LinkedHashMap<>(resolveArguments(node.get("inputs"), context));
        String inputText = render(node.getString("inputText"), context);
        if (inputText != null) arguments.put(node.getString("inputField", "input"), inputText);
        String connectionId = "DYNAMIC".equalsIgnoreCase(node.getString("connectionSource"))
                ? render(node.getString("connectionIdTemplate"), context)
                : node.getString("connectionId");
        ConnectorExecutionContext executionContext = new ConnectorExecutionContext(
                context.getRunId() + ":" + node.getId(),
                context.getTenantId(),
                context.getUserId(),
                null,
                null,
                context.getRunId(),
                node.getId(),
                Map.of("conversationId", context.getConversationId() == null ? "" : context.getConversationId()));
        ConnectorExecutionRequest request = new ConnectorExecutionRequest(
                new ConnectorKey(provider, connectorId),
                actionId,
                node.getString("installationId"),
                connectionId,
                arguments,
                executionContext);
        Object pendingTask = context.getPool().namespace(node.getId()).get("_pluginTask");
        if ("plugin".equals(provider)) {
            if (pendingTask != null) {
                request = JsonUtils.convert(
                        context.getPool().namespace(node.getId()).get("_pluginInvocation"),
                        ConnectorExecutionRequest.class);
                if (request == null) return NodeResult.failure("Plugin task invocation is missing from checkpoint");
                var attributes = new LinkedHashMap<>(request.context().attributes());
                attributes.put("pluginTask", pendingTask);
                attributes.put("pluginTaskOperation", "QUERY");
                var original = request.context();
                request = new ConnectorExecutionRequest(
                        request.connector(),
                        request.actionId(),
                        request.installationId(),
                        request.connectionId(),
                        request.arguments(),
                        new ConnectorExecutionContext(
                                original.executionId(),
                                original.tenantId(),
                                original.userId(),
                                original.agentId(),
                                original.workflowId(),
                                original.runId(),
                                original.nodeId(),
                                attributes));
            } else {
                context.getPool().put(node.getId(), "_pluginInvocation", request);
                context.getPool()
                        .put(
                                node.getId(),
                                "_pluginDeadline",
                                Instant.now()
                                        .plusSeconds(
                                                Math.max(1, Math.min(86400, node.getInt("taskTimeoutSeconds", 900))))
                                        .toString());
            }
        }
        ConnectorResult result = gateway.execute(request);
        if ("plugin".equals(provider) && result.success() && result.metadata().get("pluginTask") != null) {
            Object taskData = result.metadata().get("pluginTask");
            context.getPool().put(node.getId(), "_pluginTask", taskData);
            try {
                context.throwIfCancelled();
            } catch (RuntimeException cancelled) {
                try {
                    cancelWaiting(node, context.getPool().namespace(node.getId()));
                } catch (RuntimeException cleanupFailure) {
                    cancelled.addSuppressed(cleanupFailure);
                }
                throw cancelled;
            }
            var taskEnvelope = JsonUtils.mapper().valueToTree(taskData);
            Instant wake =
                    Instant.parse(taskEnvelope.path("task").path("nextPollAt").asText());
            Instant deadline = Instant.parse(
                    String.valueOf(context.getPool().namespace(node.getId()).get("_pluginDeadline")));
            if (Instant.now().isAfter(deadline)) {
                cancelWaiting(node, context.getPool().namespace(node.getId()));
                return NodeResult.failure("Plugin task deadline exceeded");
            }
            if (wake.isBefore(Instant.now().plusSeconds(1)))
                wake = Instant.now().plusSeconds(1);
            if (wake.isAfter(deadline)) wake = deadline;
            return NodeResult.waiting(new WorkflowWaitRequest(
                    NodeType.SLEEP_UNTIL,
                    "plugin:" + context.getRunId() + ":" + node.getId(),
                    Map.of(),
                    deadline,
                    wake,
                    UUID.randomUUID().toString()));
        }
        context.throwIfCancelled();
        if (!result.success()) {
            String message = result.error() == null
                    ? "Connector execution failed"
                    : result.error().message();
            return NodeResult.failure(message);
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("success", true);
        output.put("data", result.data());
        output.put("content", result.content());
        output.put("metadata", result.metadata());
        output.put("provider", provider);
        output.put("connectorId", connectorId);
        output.put("actionId", actionId);
        if (connectionId != null) output.put("connectionId", connectionId);
        return NodeResult.of("result", output);
    }

    @Override
    public void cancelWaiting(NodeDef node, Map<String, Object> saved) {
        if (!"plugin".equals(node.getString("provider")) || saved.get("_pluginTask") == null) return;
        var request = JsonUtils.convert(saved.get("_pluginInvocation"), ConnectorExecutionRequest.class);
        if (request == null) throw new IllegalStateException("Plugin task invocation is missing");
        var original = request.context();
        var attributes = new LinkedHashMap<>(original.attributes());
        attributes.put("pluginTask", saved.get("_pluginTask"));
        attributes.put("pluginTaskOperation", "CANCEL");
        var result = gateway.execute(new ConnectorExecutionRequest(
                request.connector(),
                request.actionId(),
                request.installationId(),
                request.connectionId(),
                request.arguments(),
                new ConnectorExecutionContext(
                        original.executionId(),
                        original.tenantId(),
                        original.userId(),
                        original.agentId(),
                        original.workflowId(),
                        original.runId(),
                        original.nodeId(),
                        attributes)));
        if (!result.success() || result.metadata().get("pluginTask") != null)
            throw new IllegalStateException("Plugin task cancellation was not confirmed");
    }

    private NodeResult sendChannelMessage(NodeDef node, ExecutionContext context) {
        if (channelConnections == null || channelRuntimes == null)
            return NodeResult.failure("Channel message runtime is unavailable");
        String channelSource = node.getString("channelSource", "REPLY_TRIGGER");
        boolean triggerChannel = "REPLY_TRIGGER".equalsIgnoreCase(channelSource)
                || "TRIGGER".equalsIgnoreCase(channelSource); // legacy saved graphs
        Map<String, Object> trigger = triggerChannel ? findTrigger(context) : Map.of();
        Map<String, Object> message = triggerChannel ? findStartObject(context, "message") : Map.of();
        String connectionId = triggerChannel
                ? text(trigger.get("connectionId"))
                : render(node.getString("channelConnectionId"), context);
        if (connectionId == null) return NodeResult.failure("Message connector connection is required");
        ChannelConnectionService.View connection = channelConnections.get(connectionId, context.getTenantId());
        if (!"ACTIVE".equals(connection.desiredStatus())) return NodeResult.failure("Message connector is disabled");
        if ("USER".equalsIgnoreCase(connection.ownerType())
                && (context.getUserId() == null || !context.getUserId().equals(connection.ownerId()))) {
            return NodeResult.failure("Current user cannot use this message connector account");
        }
        String targetId =
                triggerChannel ? text(message.get("replyTargetId")) : render(node.getString("targetId"), context);
        if (triggerChannel && targetId == null) targetId = text(message.get("senderId"));
        String conversationId = triggerChannel
                ? text(message.get("conversationId"))
                : render(node.getString("channelConversationId"), context);
        String content = render(node.getString("messageContent"), context);
        if (targetId == null) return NodeResult.failure("Message target is required");
        if (content == null) return NodeResult.failure("Message content is required");
        ChannelSendResult sent = channelRuntimes
                .require(connection.provider(), connection.runtimeNodeId())
                .sendWithResult(new ChannelOutboundMessage(
                        connection.channelId(),
                        connection.runtimeAccountId(),
                        targetId,
                        conversationId,
                        content,
                        node.getString("messageType", "TEXT"),
                        java.util.List.of(),
                        Map.of(),
                        Map.of("idempotencyKey", context.getRunId() + ":" + node.getId())));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("success", true);
        output.put("connectionId", connection.id());
        output.put("provider", connection.provider());
        output.put("channelId", connection.channelId());
        output.put("targetId", targetId);
        output.put("conversationId", conversationId);
        output.put("messageType", node.getString("messageType", "TEXT"));
        if (sent.platformMessageId() != null) output.put("messageId", sent.platformMessageId());
        output.put("metadata", sent.metadata());
        return NodeResult.of("result", output);
    }

    private static Map<String, Object> findTrigger(ExecutionContext context) {
        for (Map<String, Object> namespace : context.getPool().snapshot().values()) {
            Object value = namespace.get("triggers");
            if (value instanceof Map<?, ?> map && "connector".equalsIgnoreCase(String.valueOf(map.get("type")))) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((key, item) -> result.put(String.valueOf(key), item));
                return result;
            }
        }
        return Map.of();
    }

    private static Map<String, Object> findStartObject(ExecutionContext context, String key) {
        for (Map<String, Object> namespace : context.getPool().snapshot().values()) {
            Object value = namespace.get(key);
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((name, item) -> result.put(String.valueOf(name), item));
                return result;
            }
        }
        return Map.of();
    }

    private static String render(String template, ExecutionContext context) {
        if (template == null || template.isBlank()) return null;
        Object value = VariableResolver.render(template, context.getPool());
        return text(value);
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private static String required(NodeDef node, String key) {
        String value = node.getString(key);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Connector node requires '" + key + "'");
        return value;
    }

    private static Map<String, Object> resolveArguments(Object configured, ExecutionContext context) {
        if (!(configured instanceof Map<?, ?> values)) return Map.of();
        Map<String, Object> resolved = new LinkedHashMap<>();
        values.forEach((key, value) -> resolved.put(String.valueOf(key), resolveValue(value, context)));
        return resolved;
    }

    private static Object resolveValue(Object value, ExecutionContext context) {
        if (value instanceof String template) return VariableResolver.render(template, context.getPool());
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            map.forEach((key, item) -> resolved.put(String.valueOf(key), resolveValue(item, context)));
            return resolved;
        }
        if (value instanceof java.util.List<?> list) {
            return list.stream().map(item -> resolveValue(item, context)).toList();
        }
        return value;
    }
}

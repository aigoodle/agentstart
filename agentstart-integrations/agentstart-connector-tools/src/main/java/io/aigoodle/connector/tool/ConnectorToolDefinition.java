package io.aigoodle.connector.tool;

import io.aigoodle.connector.ConnectorActionDefinition;
import io.aigoodle.connector.ConnectorDefinition;
import io.aigoodle.connector.execution.ConnectorExecutionContext;
import io.aigoodle.connector.execution.ConnectorExecutionGateway;
import io.aigoodle.connector.execution.ConnectorExecutionRequest;
import io.aigoodle.connector.execution.ConnectorResult;
import io.aigoodle.tool.ContextualToolDefinition;
import io.aigoodle.tool.ToolMetadata;
import io.aigoodle.tool.execution.ToolExecutionContext;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exposes one provider-neutral connector action as a model-callable tool. */
public final class ConnectorToolDefinition implements ContextualToolDefinition, ToolMetadata {
    private final ConnectorDefinition connector;
    private final ConnectorActionDefinition action;
    private final ConnectorExecutionGateway gateway;

    public ConnectorToolDefinition(
            ConnectorDefinition connector, ConnectorActionDefinition action, ConnectorExecutionGateway gateway) {
        this.connector = connector;
        this.action = action;
        this.gateway = gateway;
    }

    @Override
    public String name() {
        return sanitize("connector__" + connector.key().provider() + "__"
                + connector.key().connectorId() + "__" + action.id());
    }

    @Override
    public String description() {
        return action.description();
    }

    @Override
    public String inputSchema() {
        return action.inputSchema();
    }

    @Override
    public boolean idempotent() {
        return action.idempotent();
    }

    @Override
    public Duration timeout() {
        return action.timeout();
    }

    @Override
    public Map<String, Object> metadata() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("label", connector.name() + " / " + action.name());
        values.put("category", "Connector");
        if (connector.icon() != null) values.put("icon", connector.icon());
        values.put("provider", connector.key().provider());
        values.put("connectorId", connector.key().connectorId());
        values.put("actionId", action.id());
        values.put("riskLevel", action.riskLevel().name());
        return Map.copyOf(values);
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolExecutionContext toolContext) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.putAll(toolContext.metadata());
        if (toolContext.conversationId() != null) {
            attributes.put("conversationId", toolContext.conversationId());
        }
        attributes.put("tool", name());
        ConnectorExecutionContext context = new ConnectorExecutionContext(
                toolContext.executionId(),
                toolContext.tenantId(),
                toolContext.ownerId(),
                null,
                null,
                null,
                null,
                attributes);
        ConnectorResult result = gateway.execute(
                new ConnectorExecutionRequest(connector.key(), action.id(), null, null, arguments, context));
        if (!result.success()) {
            throw new io.aigoodle.connector.ConnectorException(
                    result.error().code(), result.error().message());
        }
        if (result.metadata().get("pluginTask") != null)
            return Map.of("status", "PENDING", "task", result.metadata().get("pluginTask"));
        return result.data() != null ? result.data() : result.content();
    }

    private static String sanitize(String value) {
        String normalized = value.replaceAll("[^A-Za-z0-9_-]", "_");
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }
}

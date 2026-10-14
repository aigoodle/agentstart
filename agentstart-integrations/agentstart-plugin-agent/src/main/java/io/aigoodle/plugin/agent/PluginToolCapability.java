package io.aigoodle.plugin.agent;

import io.aigoodle.connector.execution.ConnectorExecutionContext;
import io.aigoodle.plugin.host.PluginHostCapability;
import io.aigoodle.tool.ToolRegistry;
import io.aigoodle.tool.execution.ToolExecutionContext;
import io.aigoodle.tool.execution.ToolExecutionGateway;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Bind one capability to one registered tool (including MCP), preserving governance and identity. */
public final class PluginToolCapability implements PluginHostCapability {
    private final String name;
    private final String toolName;
    private final Supplier<ToolRegistry> tools;
    private final Supplier<ToolExecutionGateway> gateway;

    public PluginToolCapability(
            String name, String toolName, Supplier<ToolRegistry> tools, Supplier<ToolExecutionGateway> gateway) {
        this.name = name;
        this.toolName = toolName;
        this.tools = tools;
        this.gateway = gateway;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Object execute(ConnectorExecutionContext identity, Map<String, Object> arguments) {
        var context = new ToolExecutionContext(
                UUID.randomUUID().toString(),
                identity.tenantId(),
                identity.userId(),
                (String) identity.attributes().get("conversationId"),
                Map.of(
                        "source",
                        "plugin",
                        "parentExecutionId",
                        identity.executionId() == null ? "" : identity.executionId()));
        return gateway.get().execute(tools.get().get(toolName), arguments, context);
    }
}

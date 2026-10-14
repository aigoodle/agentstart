package io.aigoodle.tool.mcp;

import io.aigoodle.tool.ContextualToolDefinition;
import io.aigoodle.tool.ToolDefinition;
import io.aigoodle.tool.ToolMetadata;
import io.aigoodle.tool.execution.ToolExecutionContext;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Adapts a single tool exposed by an MCP server to the {@link ToolDefinition} SPI, so it can
 * be used by agents and workflows exactly like a built-in tool. Calls are proxied to the
 * MCP server over the manager's client.
 */
public class McpToolDefinition implements ContextualToolDefinition, ToolMetadata {

    private final McpSyncClient client;
    private final String name;
    private final String description;
    private final String inputSchema;
    private final String serverId;

    /** Backward-compatible constructor for applications that create MCP tools directly. */
    public McpToolDefinition(McpSyncClient client, String name, String description, String inputSchema) {
        this(client, "mcp", name, description, inputSchema);
    }

    public McpToolDefinition(
            McpSyncClient client, String serverId, String name, String description, String inputSchema) {
        this.client = client;
        this.serverId = serverId;
        this.name = name;
        this.description = description == null ? name : description;
        this.inputSchema = inputSchema;
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("source", "MCP", "category", "MCP", "provider", serverId, "mcpServerId", serverId);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String inputSchema() {
        return inputSchema == null ? ContextualToolDefinition.super.inputSchema() : inputSchema;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return execute(arguments, ToolExecutionContext.anonymous());
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(name, arguments);
        Object authorization = context == null ? null : context.metadata().get(McpInvocationHeaders.AUTHORIZATION);
        McpSchema.CallToolResult callResult;
        try (McpInvocationHeaders.Scope ignored = McpInvocationHeaders.withAuthorization(authorization)) {
            callResult = client.callTool(request);
        }
        String responseText = callResult.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .collect(Collectors.joining("\n"));
        if (Boolean.TRUE.equals(callResult.isError())) {
            return "error: " + responseText;
        }
        return responseText;
    }
}

package io.aigoodle.tool.mcp;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.tool.ToolDefinition;
import io.aigoodle.tool.ToolProvider;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ToolProvider} that surfaces the tools of every configured MCP server. The
 * tools join the {@code ToolRegistry} alongside built-in tools, so agents can call them
 * transparently. This is the MCP integration the {@code ToolProvider} SPI was designed
 * for. Tool discovery is cached after the first call; a failing server is skipped (it
 * never breaks startup).
 */
public class McpToolProvider implements ToolProvider {

    private static final Logger logger = LoggerFactory.getLogger(McpToolProvider.class);

    private final McpClientManager clientManager;
    private volatile List<ToolDefinition> discoveredTools;

    public McpToolProvider(McpClientManager clientManager) {
        this.clientManager = clientManager;
    }

    /** Invalidates discovery after an administrator changes the server catalog. */
    public synchronized void refresh() {
        discoveredTools = null;
    }

    @Override
    public List<ToolDefinition> getTools() {
        if (discoveredTools != null) {
            return discoveredTools;
        }
        synchronized (this) {
            if (discoveredTools == null) {
                discoveredTools = discoverTools();
            }
            return discoveredTools;
        }
    }

    private List<ToolDefinition> discoverTools() {
        List<ToolDefinition> tools = new ArrayList<>();
        for (McpProperties.Server server : clientManager.servers()) {
            if (!server.isEnabled()) continue;
            try {
                McpSyncClient client = clientManager.client(server);
                List<McpSchema.Tool> serverTools = client.listTools().tools();
                serverTools.stream()
                        .map(tool -> toAgentTool(client, server.getId(), tool))
                        .forEach(tools::add);
                logger.info("MCP server '{}' contributed {} tool(s)", server.getName(), serverTools.size());
            } catch (RuntimeException discoveryFailure) {
                logger.error(
                        "Failed to load tools from MCP server '{}': {}",
                        server.getName(),
                        discoveryFailure.getMessage());
            }
        }
        return List.copyOf(tools);
    }

    private static ToolDefinition toAgentTool(McpSyncClient client, String serverId, McpSchema.Tool tool) {
        String inputSchema = tool.inputSchema() == null ? null : JsonUtils.toJson(tool.inputSchema());
        return new McpToolDefinition(client, serverId, tool.name(), tool.description(), inputSchema);
    }
}

package io.aigoodle.tool.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * A minimal real MCP server run as a subprocess over stdio by {@code McpClientTest}.
 * Exposes one tool, {@code greet}. Writes nothing to stdout except the MCP protocol.
 */
public final class McpTestServer {

    private McpTestServer() {}

    public static void main(String[] args) throws InterruptedException {
        McpJsonMapper mapper = new JacksonMcpJsonMapper(
                tools.jackson.databind.json.JsonMapper.builder().build());
        StdioServerTransportProvider transport = new StdioServerTransportProvider(mapper);

        McpSchema.Tool greet = McpSchema.Tool.builder()
                .name("greet")
                .description("Greet a person by name")
                .inputSchema(mapper, "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}")
                .build();

        McpServerFeatures.SyncToolSpecification greetSpec = McpServerFeatures.SyncToolSpecification.builder()
                .tool(greet)
                .callHandler((exchange, request) -> {
                    Object name = request.arguments().getOrDefault("name", "world");
                    return McpSchema.CallToolResult.builder()
                            .addTextContent("Hello, " + name + "!")
                            .build();
                })
                .build();

        McpServer.sync(transport)
                .serverInfo("test-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tools(greetSpec)
                .build();

        // The transport serves requests on a background thread; keep the process alive.
        Thread.currentThread().join();
    }
}

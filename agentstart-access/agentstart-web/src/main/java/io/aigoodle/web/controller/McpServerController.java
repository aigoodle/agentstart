package io.aigoodle.web.controller;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.tool.ToolRegistry;
import io.aigoodle.tool.mcp.McpClientManager;
import io.aigoodle.tool.mcp.McpInvocationHeaders;
import io.aigoodle.tool.mcp.McpProperties;
import io.aigoodle.tool.mcp.McpToolProvider;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.bind.annotation.*;

/** Runtime MCP catalog. Secrets are accepted for stdio child-process env and never returned. */
@RestController
@ConditionalOnClass(McpClientManager.class)
@RequestMapping("/mcp-servers")
public class McpServerController {
    private final ObjectProvider<McpClientManager> clients;
    private final ObjectProvider<McpToolProvider> provider;
    private final ObjectProvider<ToolRegistry> tools;

    public McpServerController(
            ObjectProvider<McpClientManager> clients,
            ObjectProvider<McpToolProvider> provider,
            ObjectProvider<ToolRegistry> tools) {
        this.clients = clients;
        this.provider = provider;
        this.tools = tools;
    }

    public record ServerView(
            String id,
            String name,
            String transport,
            boolean enabled,
            String url,
            String command,
            List<String> args,
            boolean envConfigured,
            String status,
            Integer toolCount,
            String lastError) {}

    public record SaveServer(
            String id,
            String name,
            String transport,
            Boolean enabled,
            String url,
            String command,
            List<String> args,
            Map<String, String> env) {}

    public record TestResult(boolean success, String status, Integer toolCount, String message, long durationMs) {}

    @GetMapping
    public ApiResponse<List<ServerView>> list() {
        return ApiResponse.ok(clientManager().servers().stream().map(this::view).toList());
    }

    @PostMapping
    public ApiResponse<ServerView> save(@RequestBody SaveServer request) {
        McpClientManager clients = clientManager();
        McpProperties.Server server = new McpProperties.Server();
        server.setId(request.id());
        server.setName(request.name());
        server.setType(
                request.transport() == null ? "stdio" : request.transport().toLowerCase());
        server.setEnabled(request.enabled() == null || request.enabled());
        server.setUrl(request.url());
        server.setCommand(request.command());
        server.setArgs(request.args() == null ? List.of() : request.args());
        server.setEnv(request.env() == null ? Map.of() : request.env());
        clients.upsert(server);
        refreshTools();
        return ApiResponse.ok(view(server));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        clientManager().remove(id);
        refreshTools();
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/test")
    public CompletableFuture<ApiResponse<TestResult>> test(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-MCP-Authorization", required = false) String mcpAuthorization) {
        String forwardedAuthorization =
                mcpAuthorization == null || mcpAuthorization.isBlank() ? authorization : mcpAuthorization;
        return CompletableFuture.supplyAsync(() -> {
            try (McpInvocationHeaders.Scope ignored = McpInvocationHeaders.withAuthorization(forwardedAuthorization)) {
                return testBlocking(id);
            }
        });
    }

    private ApiResponse<TestResult> testBlocking(String id) {
        long startedAt = System.nanoTime();
        try {
            int count = clientManager().test(id);
            refreshTools();
            return ApiResponse.ok(
                    new TestResult(true, "CONNECTED", count, "连接成功，发现 " + count + " 个工具", elapsedMs(startedAt)));
        } catch (RuntimeException failure) {
            return ApiResponse.ok(
                    new TestResult(false, "ERROR", null, diagnosticMessage(failure), elapsedMs(startedAt)));
        }
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static String diagnosticMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        if (message.contains("Authorization error when sending message")) {
            return "MCP 服务拒绝鉴权，请在测试请求中提供有效的 X-MCP-Authorization: Bearer <业务令牌>，" + "或 Authorization: Bearer <业务令牌>";
        }
        return message.length() > 600 ? message.substring(0, 600) + "…" : message;
    }

    private McpClientManager clientManager() {
        return clients.getObject();
    }

    private void refreshTools() {
        provider.getObject().refresh();
        tools.getObject().refresh();
    }

    private ServerView view(McpProperties.Server server) {
        return new ServerView(
                server.getId(),
                server.getName(),
                server.getType().toUpperCase(),
                server.isEnabled(),
                server.getUrl(),
                server.getCommand(),
                server.getArgs(),
                !server.getEnv().isEmpty(),
                "CONFIGURED",
                null,
                null);
    }
}

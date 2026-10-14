package io.aigoodle.web.controller;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.tool.ToolDefinition;
import io.aigoodle.tool.ToolMetadata;
import io.aigoodle.tool.ToolRegistry;
import io.aigoodle.tool.custom.CustomHttpToolSpec;
import io.aigoodle.tool.custom.CustomToolManager;
import io.aigoodle.tool.execution.ToolExecutionContext;
import io.aigoodle.tool.execution.ToolExecutionContextProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists tools available to agents and lets the frontend probe one to check what it does.
 */
@RestController
@ConditionalOnBean(ToolRegistry.class)
@RequestMapping("/tools")
public class ToolController {

    private final ToolRegistry toolRegistry;
    private final CustomToolManager customToolManager;
    private final ToolExecutionContextProvider contextProvider;

    public ToolController(
            ToolRegistry toolRegistry,
            ObjectProvider<CustomToolManager> customToolManager,
            ToolExecutionContextProvider contextProvider) {
        this.toolRegistry = toolRegistry;
        this.customToolManager = customToolManager.getIfAvailable();
        this.contextProvider = contextProvider;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(toolRegistry.all().stream().map(this::asView).toList());
    }

    @GetMapping("/{name}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String name) {
        return ApiResponse.ok(asView(toolRegistry.get(name)));
    }

    /**
     * Test-drive a tool with the given args. Bypasses the agent — useful for the
     * "try it" button on the tool details page.
     */
    @PostMapping("/{name}/invoke")
    public ApiResponse<Object> invoke(
            @PathVariable String name,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-MCP-Authorization", required = false) String mcpAuthorization,
            @RequestBody(required = false) Map<String, Object> args) {
        ToolExecutionContext current = contextProvider.currentContext();
        Map<String, Object> metadata = new LinkedHashMap<>(current.metadata());
        String forwardedAuthorization =
                mcpAuthorization == null || mcpAuthorization.isBlank() ? authorization : mcpAuthorization;
        if (forwardedAuthorization != null && !forwardedAuthorization.isBlank()) {
            metadata.put("authorization", forwardedAuthorization);
        }
        ToolExecutionContext invocation = new ToolExecutionContext(
                current.executionId(), current.tenantId(), current.ownerId(), current.conversationId(), metadata);
        return ApiResponse.ok(toolRegistry.execute(name, args, invocation));
    }

    @GetMapping("/custom")
    public ApiResponse<List<CustomHttpToolSpec>> listCustom() {
        return ApiResponse.ok(requireCustomManager().specs());
    }

    @PostMapping("/custom")
    public ApiResponse<Map<String, Object>> saveCustom(@RequestBody CustomHttpToolSpec request) {
        if (toolRegistry.has(request.getName())) {
            ToolDefinition existing = toolRegistry.get(request.getName());
            boolean isCustom = existing instanceof ToolMetadata metadata
                    && Boolean.TRUE.equals(metadata.metadata().get("custom"));
            if (!isCustom) throw new IllegalArgumentException("A system tool already uses name: " + request.getName());
        }
        requireCustomManager().upsert(request);
        toolRegistry.refresh();
        return ApiResponse.ok(asView(toolRegistry.get(request.getName())));
    }

    @DeleteMapping("/custom/{name}")
    public ApiResponse<Boolean> deleteCustom(@PathVariable String name) {
        boolean removed = requireCustomManager().remove(name);
        toolRegistry.refresh();
        return ApiResponse.ok(removed);
    }

    private Map<String, Object> asView(ToolDefinition tool) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", tool.name());
        view.put("description", tool.description());
        view.put("inputSchema", tool.inputSchema());
        view.put("source", "system");
        view.put("category", "system");
        view.put("custom", false);
        if (tool instanceof ToolMetadata metadata) view.putAll(metadata.metadata());
        return view;
    }

    private CustomToolManager requireCustomManager() {
        if (customToolManager == null) throw new IllegalStateException("Custom tools are not enabled");
        return customToolManager;
    }
}

package io.aigoodle.tool;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.tool.adapter.ToolDefinitionCallback;
import io.aigoodle.tool.execution.ToolExecutionContext;
import io.aigoodle.tool.execution.ToolExecutionContextProvider;
import io.aigoodle.tool.execution.ToolExecutionGateway;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.tool.ToolCallback;

/**
 * Central registry of all {@link ToolDefinition}s — statically declared beans plus those
 * contributed by {@link ToolProvider}s (e.g. MCP). Exposes them by name and as Spring
 * AI {@link ToolCallback}s for agents.
 */
public class ToolRegistry {

    private final List<ToolDefinition> declaredTools;
    private final List<ToolProvider> toolProviders;
    private final ToolExecutionGateway executionGateway;
    private final ToolExecutionContextProvider contextProvider;
    private volatile Map<String, ToolDefinition> toolsByName;

    public ToolRegistry(List<ToolDefinition> declaredTools, List<ToolProvider> toolProviders) {
        this(declaredTools, toolProviders, ToolExecutionGateway.direct(), ToolExecutionContextProvider.anonymous());
    }

    public ToolRegistry(
            List<ToolDefinition> declaredTools,
            List<ToolProvider> toolProviders,
            ToolExecutionGateway executionGateway,
            ToolExecutionContextProvider contextProvider) {
        this.declaredTools = declaredTools == null ? List.of() : List.copyOf(declaredTools);
        this.toolProviders = toolProviders == null ? List.of() : List.copyOf(toolProviders);
        this.executionGateway = executionGateway == null ? ToolExecutionGateway.direct() : executionGateway;
        this.contextProvider = contextProvider == null ? ToolExecutionContextProvider.anonymous() : contextProvider;
        refresh();
    }

    /** Rebuilds an immutable tool snapshot after a dynamic provider changes. */
    public synchronized void refresh() {
        Map<String, ToolDefinition> registeredTools = new LinkedHashMap<>();
        registerAll(registeredTools, declaredTools);
        registerProvidedTools(registeredTools, toolProviders);
        this.toolsByName = Collections.unmodifiableMap(registeredTools);
    }

    /** Registers or replaces a runtime-created tool (for example an HTTP API tool). */
    public synchronized void register(ToolDefinition tool) {
        Objects.requireNonNull(tool, "tool");
        Map<String, ToolDefinition> updated = new LinkedHashMap<>(toolsByName);
        updated.put(tool.name(), tool);
        this.toolsByName = Collections.unmodifiableMap(updated);
    }

    /** Removes a runtime-created tool. Statically declared tools return after {@link #refresh()}. */
    public synchronized boolean unregister(String toolName) {
        if (!toolsByName.containsKey(toolName)) return false;
        Map<String, ToolDefinition> updated = new LinkedHashMap<>(toolsByName);
        updated.remove(toolName);
        this.toolsByName = Collections.unmodifiableMap(updated);
        return true;
    }

    public ToolDefinition get(String toolName) {
        ToolDefinition tool = toolsByName.get(toolName);
        if (tool == null) {
            throw new PlatformException(
                    "tool_not_found", "No tool named '" + toolName + "'. Available: " + toolsByName.keySet(), null);
        }
        return tool;
    }

    public boolean has(String toolName) {
        return toolsByName.containsKey(toolName);
    }

    public List<ToolDefinition> all() {
        return List.copyOf(toolsByName.values());
    }

    /** A call-owned tool snapshot using this registry's execution gateway and identity provider. */
    public ToolRegistry scoped(List<ToolDefinition> definitions) {
        if (definitions.stream().map(ToolDefinition::name).distinct().count() != definitions.size()) {
            throw new IllegalArgumentException("Duplicate tool names in execution scope");
        }
        return new ToolRegistry(definitions, List.of(), executionGateway, contextProvider);
    }

    public List<String> names() {
        return List.copyOf(toolsByName.keySet());
    }

    public Object execute(String toolName, Map<String, Object> arguments) {
        return execute(toolName, arguments, contextProvider.currentContext());
    }

    public Object execute(String toolName, Map<String, Object> arguments, ToolExecutionContext context) {
        return executionGateway.execute(
                get(toolName),
                arguments == null ? Map.of() : arguments,
                context == null ? ToolExecutionContext.anonymous() : context);
    }

    /** All tools adapted to Spring AI callbacks, for handing to a ChatClient/agent. */
    public List<ToolCallback> toolCallbacks() {
        return toolsByName.values().stream()
                .map(tool -> new ToolDefinitionCallback(tool, executionGateway, contextProvider))
                .map(ToolCallback.class::cast)
                .toList();
    }

    /** Callbacks for a named subset (unknown names are skipped). */
    public List<ToolCallback> toolCallbacks(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        return toolNames.stream()
                .map(toolsByName::get)
                .filter(Objects::nonNull)
                .map(tool -> new ToolDefinitionCallback(tool, executionGateway, contextProvider))
                .map(ToolCallback.class::cast)
                .toList();
    }

    private static void registerProvidedTools(
            Map<String, ToolDefinition> registeredTools, List<ToolProvider> toolProviders) {
        if (toolProviders == null) {
            return;
        }
        for (ToolProvider toolProvider : toolProviders) {
            registerAll(registeredTools, toolProvider.getTools());
        }
    }

    private static void registerAll(Map<String, ToolDefinition> registeredTools, List<ToolDefinition> tools) {
        if (tools == null) {
            return;
        }
        for (ToolDefinition tool : tools) {
            registeredTools.put(tool.name(), tool);
        }
    }
}

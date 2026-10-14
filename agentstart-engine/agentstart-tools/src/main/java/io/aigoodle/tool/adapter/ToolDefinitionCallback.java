package io.aigoodle.tool.adapter;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.tool.ToolDefinition;
import io.aigoodle.tool.execution.ToolExecutionContextProvider;
import io.aigoodle.tool.execution.ToolExecutionGateway;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;

/**
 * Adapts an {@link ToolDefinition} to a Spring AI {@link ToolCallback}, so any tool can be
 * handed to a {@code ChatClient}/agent for function calling. The model's JSON tool
 * input is parsed into a map and the tool's result is serialised back to a string.
 */
public class ToolDefinitionCallback implements ToolCallback {

    private final ToolDefinition tool;
    private final ToolExecutionGateway executionGateway;
    private final ToolExecutionContextProvider contextProvider;

    /** Compatibility constructor for an already-governed definition. */
    public ToolDefinitionCallback(ToolDefinition tool) {
        this(tool, ToolExecutionGateway.direct(), ToolExecutionContextProvider.anonymous());
    }

    public ToolDefinitionCallback(
            ToolDefinition tool, ToolExecutionGateway executionGateway, ToolExecutionContextProvider contextProvider) {
        this.tool = tool;
        this.executionGateway = executionGateway == null ? ToolExecutionGateway.direct() : executionGateway;
        this.contextProvider = contextProvider == null ? ToolExecutionContextProvider.anonymous() : contextProvider;
    }

    @Override
    public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
        return DefaultToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(tool.inputSchema())
                .build();
    }

    @Override
    public String call(String toolInput) {
        Map<String, Object> parsedArguments = JsonUtils.parseMap(toolInput);
        Map<String, Object> arguments = parsedArguments == null ? Map.of() : parsedArguments;
        Object toolResult = executionGateway.execute(tool, arguments, contextProvider.currentContext());
        if (toolResult == null) {
            return "";
        }
        return toolResult instanceof String text ? text : JsonUtils.toJson(toolResult);
    }
}

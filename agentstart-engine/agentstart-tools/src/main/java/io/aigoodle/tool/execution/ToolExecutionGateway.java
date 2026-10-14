package io.aigoodle.tool.execution;

import io.aigoodle.tool.ContextualToolDefinition;
import io.aigoodle.tool.ToolDefinition;
import java.util.Map;

/** Single governed execution boundary for every tool invocation. */
@FunctionalInterface
public interface ToolExecutionGateway {
    Object execute(ToolDefinition tool, Map<String, Object> arguments, ToolExecutionContext context);

    static ToolExecutionGateway direct() {
        return (tool, arguments, context) -> invoke(
                tool,
                arguments == null ? Map.of() : arguments,
                context == null ? ToolExecutionContext.anonymous() : context);
    }

    static Object invoke(ToolDefinition tool, Map<String, Object> arguments, ToolExecutionContext context) {
        if (tool instanceof ContextualToolDefinition contextual) {
            return contextual.execute(arguments, context);
        }
        return tool.execute(arguments);
    }
}

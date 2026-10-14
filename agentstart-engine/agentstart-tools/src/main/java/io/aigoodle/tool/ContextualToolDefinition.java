package io.aigoodle.tool;

import io.aigoodle.tool.execution.ToolExecutionContext;
import java.util.Map;

/** Optional extension for tools that require tenant/caller context at invocation time. */
public interface ContextualToolDefinition extends ToolDefinition {
    Object execute(Map<String, Object> arguments, ToolExecutionContext context);

    @Override
    default Object execute(Map<String, Object> arguments) {
        return execute(arguments, ToolExecutionContext.anonymous());
    }
}

package io.aigoodle.tool.execution;

import io.aigoodle.tool.ToolDefinition;
import java.util.Map;

/** Policy hook for authorization, quotas, network allowlists or argument screening. */
@FunctionalInterface
public interface ToolExecutionPolicy {
    Decision evaluate(ToolDefinition tool, Map<String, Object> arguments, ToolExecutionContext context);

    record Decision(boolean allowed, String reason) {
        public static Decision allow() {
            return new Decision(true, null);
        }

        public static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }
}

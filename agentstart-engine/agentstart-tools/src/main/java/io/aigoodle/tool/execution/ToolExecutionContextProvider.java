package io.aigoodle.tool.execution;

/**
 * Resolves the trusted caller snapshot used by framework-created tool callbacks.
 * Hosts may replace this bean when identity lives outside {@code UserContextHolder}
 * (for example Reactor Context, a job envelope, or a message-consumer context).
 */
@FunctionalInterface
public interface ToolExecutionContextProvider {

    ToolExecutionContext currentContext();

    static ToolExecutionContextProvider anonymous() {
        return ToolExecutionContext::anonymous;
    }
}

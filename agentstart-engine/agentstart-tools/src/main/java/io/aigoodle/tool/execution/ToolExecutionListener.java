package io.aigoodle.tool.execution;

/** Best-effort sink used by observability and durable Agent Run projections. */
@FunctionalInterface
public interface ToolExecutionListener {
    void onExecution(ToolExecutionRecord record);
}

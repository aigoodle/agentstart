package io.aigoodle.tool.execution;

import java.time.Duration;

/** Immutable audit record emitted after every allowed or rejected invocation. */
public record ToolExecutionRecord(
        String executionId,
        String toolName,
        Status status,
        int attempts,
        Duration duration,
        String error,
        ToolExecutionContext context) {
    public enum Status {
        SUCCEEDED,
        FAILED,
        TIMED_OUT,
        DENIED,
        SATURATED,
        CANCELLED
    }
}

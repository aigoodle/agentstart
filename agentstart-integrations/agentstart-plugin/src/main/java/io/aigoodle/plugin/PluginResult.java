package io.aigoodle.plugin;

import io.aigoodle.connector.execution.ConnectorResult;

/** Version-one response envelope, shared by in-process and HTTP implementations. */
public record PluginResult(ConnectorResult result, PluginTask task) {
    public PluginResult(ConnectorResult result) {
        this(result, null);
    }

    public PluginResult {
        if ((result == null) == (task == null))
            throw new IllegalArgumentException("Exactly one result or pending task is required");
    }

    public static PluginResult success(Object output) {
        return new PluginResult(ConnectorResult.success(output));
    }

    public static PluginResult failure(String code, String message, boolean retryable) {
        return new PluginResult(ConnectorResult.failure(code, message, retryable));
    }

    public static PluginResult pending(PluginTask task) {
        return new PluginResult(null, task);
    }
}

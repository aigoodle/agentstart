package io.aigoodle.plugin;

import io.aigoodle.connector.execution.ConnectorExecutionContext;
import java.util.Map;

/** Call-scoped access only. Never retain this object in a singleton plugin. */
public record PluginContext(
        ConnectorExecutionContext identity,
        Map<String, Object> configuration,
        Map<String, Object> credentials,
        PluginHost host) {
    public PluginContext {
        configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        credentials = credentials == null ? Map.of() : Map.copyOf(credentials);
        if (identity == null || host == null) throw new IllegalArgumentException("identity and host are required");
    }

    @Override
    public String toString() {
        return "PluginContext[identity=" + identity.executionId()
                + ", configuration=<redacted>, credentials=<redacted>]";
    }
}

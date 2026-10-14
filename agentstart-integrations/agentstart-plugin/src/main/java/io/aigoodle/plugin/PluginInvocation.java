package io.aigoodle.plugin;

import java.util.Map;

/** Resolved input, separate from connection configuration and host identity. */
public record PluginInvocation(String actionId, Map<String, Object> inputs) {
    public PluginInvocation {
        if (actionId == null || actionId.isBlank()) throw new IllegalArgumentException("actionId is required");
        inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
    }
}

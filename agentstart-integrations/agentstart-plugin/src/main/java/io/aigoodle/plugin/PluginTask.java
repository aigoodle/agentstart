package io.aigoodle.plugin;

import java.time.Instant;
import java.util.Map;

/** Serializable remote job handle. State must not contain credentials or large media blobs. */
public record PluginTask(String id, Map<String, Object> state, Instant nextPollAt) {
    public PluginTask {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Task id is required");
        state = state == null ? Map.of() : Map.copyOf(state);
        nextPollAt = nextPollAt == null ? Instant.now().plusSeconds(5) : nextPollAt;
    }
}

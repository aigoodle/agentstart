package io.aigoodle.channel.api;

import java.util.Map;

public record ConnectionTestResult(boolean success, String code, String message, Map<String, Object> metadata) {
    public ConnectionTestResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ConnectionTestResult ok() {
        return new ConnectionTestResult(true, "ok", "Connected", Map.of());
    }
}

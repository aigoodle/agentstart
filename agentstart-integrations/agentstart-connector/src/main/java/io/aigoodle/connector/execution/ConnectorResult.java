package io.aigoodle.connector.execution;

import java.util.List;
import java.util.Map;

public record ConnectorResult(
        boolean success, Object data, List<ConnectorContent> content, Error error, Map<String, Object> metadata) {
    public record Error(String code, String message, boolean retryable) {}

    public ConnectorResult {
        content = content == null ? List.of() : List.copyOf(content);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ConnectorResult success(Object data) {
        return new ConnectorResult(true, data, List.of(), null, Map.of());
    }

    public static ConnectorResult failure(String code, String message, boolean retryable) {
        return new ConnectorResult(false, null, List.of(), new Error(code, message, retryable), Map.of());
    }
}

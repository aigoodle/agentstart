package io.aigoodle.connector.execution;

import java.util.Map;

public record ConnectorContent(Type type, String text, String uri, String mediaType, Map<String, Object> metadata) {
    public enum Type {
        TEXT,
        JSON,
        LINK,
        IMAGE,
        FILE,
        RESOURCE
    }

    public ConnectorContent {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ConnectorContent text(String value) {
        return new ConnectorContent(Type.TEXT, value, null, "text/plain", Map.of());
    }
}

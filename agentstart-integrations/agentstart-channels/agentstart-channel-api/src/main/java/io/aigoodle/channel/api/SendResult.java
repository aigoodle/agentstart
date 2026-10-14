package io.aigoodle.channel.api;

import java.util.Map;

public record SendResult(boolean accepted, String platformMessageId, Map<String, Object> metadata) {
    public SendResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static SendResult accepted(String id) {
        return new SendResult(true, id, Map.of());
    }
}

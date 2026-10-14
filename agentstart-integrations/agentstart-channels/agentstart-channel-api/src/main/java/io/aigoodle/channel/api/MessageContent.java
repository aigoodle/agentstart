package io.aigoodle.channel.api;

import java.util.Map;

public record MessageContent(
        MessageType type,
        String text,
        String url,
        String name,
        String mimeType,
        Long size,
        Map<String, Object> payload) {
    public MessageContent {
        type = type == null ? MessageType.UNKNOWN : type;
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static MessageContent text(String text) {
        return new MessageContent(MessageType.TEXT, text, null, null, null, null, Map.of());
    }
}

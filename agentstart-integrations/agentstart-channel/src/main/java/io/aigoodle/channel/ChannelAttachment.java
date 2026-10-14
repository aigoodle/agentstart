package io.aigoodle.channel;

import java.util.Map;

/** Provider-neutral attachment reference. URLs may be short-lived and should not contain credentials. */
public record ChannelAttachment(
        String type, String url, String name, String mimeType, Long size, Map<String, Object> metadata) {
    public ChannelAttachment {
        type = type == null || type.isBlank() ? "FILE" : type.trim().toUpperCase();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}

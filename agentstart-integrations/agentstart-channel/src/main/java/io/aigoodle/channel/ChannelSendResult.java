package io.aigoodle.channel;

import java.util.Map;

/** Provider acknowledgement for an outbound message. */
public record ChannelSendResult(String platformMessageId, Map<String, Object> metadata) {
    public ChannelSendResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ChannelSendResult accepted() {
        return new ChannelSendResult(null, Map.of());
    }
}

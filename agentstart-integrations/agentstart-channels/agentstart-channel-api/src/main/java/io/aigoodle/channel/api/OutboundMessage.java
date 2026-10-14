package io.aigoodle.channel.api;

import java.util.List;
import java.util.Map;

public record OutboundMessage(
        String idempotencyKey,
        String conversationId,
        String targetId,
        String replyToMessageId,
        List<MessageContent> contents,
        Map<String, Object> metadata) {
    public OutboundMessage {
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId is required");
        contents = contents == null ? List.of() : List.copyOf(contents);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static OutboundMessage text(String key, String conversationId, String targetId, String text) {
        return new OutboundMessage(key, conversationId, targetId, null, List.of(MessageContent.text(text)), Map.of());
    }
}

package io.aigoodle.channel.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record InboundMessage(
        String messageId,
        String channelId,
        String accountId,
        String conversationId,
        ConversationType conversationType,
        String senderId,
        String senderName,
        String replyTargetId,
        List<MessageContent> contents,
        Instant timestamp,
        Map<String, Object> metadata) {
    public InboundMessage {
        require(messageId, "messageId");
        require(channelId, "channelId");
        require(accountId, "accountId");
        require(conversationId, "conversationId");
        require(replyTargetId, "replyTargetId");
        conversationType = conversationType == null ? ConversationType.UNKNOWN : conversationType;
        contents = contents == null ? List.of() : List.copyOf(contents);
        timestamp = timestamp == null ? Instant.now() : timestamp;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String text() {
        return contents.stream()
                .filter(c -> c.type() == MessageType.TEXT || c.type() == MessageType.MARKDOWN)
                .map(MessageContent::text)
                .filter(v -> v != null && !v.isBlank())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}

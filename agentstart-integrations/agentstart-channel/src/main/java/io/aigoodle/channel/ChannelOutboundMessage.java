package io.aigoodle.channel;

import java.util.List;
import java.util.Map;

public record ChannelOutboundMessage(
        String channelId,
        String accountId,
        String targetId,
        String conversationId,
        String content,
        String messageType,
        List<ChannelAttachment> attachments,
        Map<String, Object> contentPayload,
        Map<String, Object> metadata) {
    public ChannelOutboundMessage {
        messageType = messageType == null || messageType.isBlank()
                ? "TEXT"
                : messageType.trim().toUpperCase();
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        contentPayload = contentPayload == null ? Map.of() : Map.copyOf(contentPayload);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public ChannelOutboundMessage(
            String channelId,
            String accountId,
            String targetId,
            String conversationId,
            String content,
            Map<String, Object> metadata) {
        this(channelId, accountId, targetId, conversationId, content, "TEXT", List.of(), Map.of(), metadata);
    }
}

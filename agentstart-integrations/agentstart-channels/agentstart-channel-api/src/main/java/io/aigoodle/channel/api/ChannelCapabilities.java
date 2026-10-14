package io.aigoodle.channel.api;

import java.util.Set;

public record ChannelCapabilities(
        Set<MessageType> inbound,
        Set<MessageType> outbound,
        boolean webhook,
        boolean streaming,
        boolean groupChat,
        boolean deliveryReceipts) {
    public ChannelCapabilities {
        inbound = inbound == null ? Set.of() : Set.copyOf(inbound);
        outbound = outbound == null ? Set.of() : Set.copyOf(outbound);
    }

    public static ChannelCapabilities text() {
        return new ChannelCapabilities(Set.of(MessageType.TEXT), Set.of(MessageType.TEXT), true, false, true, false);
    }
}

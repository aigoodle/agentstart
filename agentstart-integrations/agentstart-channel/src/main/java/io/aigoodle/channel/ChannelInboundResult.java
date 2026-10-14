package io.aigoodle.channel;

import java.util.Map;

public record ChannelInboundResult(boolean handled, String reply, String code, Map<String, Object> metadata) {
    public ChannelInboundResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ChannelInboundResult unhandled() {
        return new ChannelInboundResult(false, null, "unhandled", Map.of());
    }

    public static ChannelInboundResult managedSilent(Map<String, Object> metadata) {
        java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>(metadata);
        values.put("managed", true);
        return new ChannelInboundResult(false, null, "unbound_silent", values);
    }

    public static ChannelInboundResult reply(String reply, Map<String, Object> metadata) {
        return new ChannelInboundResult(true, reply, "handled", metadata);
    }
}

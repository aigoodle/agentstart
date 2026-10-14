package io.aigoodle.channel;

import java.util.Map;

/** Desired state submitted to a channel runtime. Configuration may contain secrets. */
public record SaveChannelAccountRequest(
        String channelId, String accountId, String name, boolean enabled, Map<String, Object> configuration) {

    public SaveChannelAccountRequest {
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channelId is required");
        if (accountId == null || accountId.isBlank()) throw new IllegalArgumentException("accountId is required");
        configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
    }
}

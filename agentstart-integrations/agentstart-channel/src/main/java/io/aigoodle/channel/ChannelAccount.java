package io.aigoodle.channel;

import java.time.Instant;
import java.util.Map;

/** Secret-free runtime view of one configured channel account. */
public record ChannelAccount(
        String provider,
        String channelId,
        String accountId,
        String name,
        boolean enabled,
        boolean configured,
        boolean running,
        boolean connected,
        Instant lastConnectedAt,
        String lastError,
        Map<String, Object> metadata) {

    public ChannelAccount {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}

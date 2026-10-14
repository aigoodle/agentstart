package io.aigoodle.channel;

import java.time.Duration;

/**
 * Low-cardinality operational signals for channel traffic. Implementations must never use
 * tenant, account, conversation, message or actor identifiers as metric tags.
 */
public interface ChannelMessageObserver {
    ChannelMessageObserver NOOP = new ChannelMessageObserver() {};

    default void outboundRateLimited(String provider, String channelId, String runtimeNodeId, Duration delay) {}

    default void outboundFinished(
            String provider, String channelId, String runtimeNodeId, String outcome, Duration duration) {}

    default void conversationProjectionFinished(
            String provider, String channelId, String runtimeNodeId, String outcome) {}
}

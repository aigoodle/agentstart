package io.aigoodle.channel;

import java.util.List;

/** Runtime boundary for long-lived channel accounts such as QQBot, Slack or email listeners. */
public interface ChannelRuntimeProvider {
    String type();

    default String nodeId() {
        return type() + "-default";
    }

    List<ChannelDefinition> discoverChannels();

    List<ChannelAccount> accounts(String channelId);

    ChannelAccount saveAccount(SaveChannelAccountRequest request);

    ChannelAccount testAccount(String channelId, String accountId);

    void deleteAccount(String channelId, String accountId);

    default void send(ChannelOutboundMessage message) {
        throw new UnsupportedOperationException("outbound messages are not supported by " + type());
    }

    default ChannelSendResult sendWithResult(ChannelOutboundMessage message) {
        send(message);
        return ChannelSendResult.accepted();
    }
}

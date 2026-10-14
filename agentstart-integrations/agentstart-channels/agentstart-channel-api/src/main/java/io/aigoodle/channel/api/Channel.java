package io.aigoodle.channel.api;

/** Minimal platform extension: translate inbound and outbound messages only. */
public interface Channel<C> {
    String id();

    Class<C> configType();

    ChannelDescriptor descriptor();

    ConnectionTestResult test(C configuration);

    SendResult send(C configuration, OutboundMessage message);

    default ChannelSession connect(C configuration, InboundMessageSink sink) {
        return ChannelSession.noop();
    }
}

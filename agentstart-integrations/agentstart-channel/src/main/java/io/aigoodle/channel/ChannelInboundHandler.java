package io.aigoodle.channel;

import java.util.Optional;

/** Host applications publish handlers to route channel messages to an agent, workflow or queue. */
public interface ChannelInboundHandler {
    boolean supports(ChannelInboundEvent event);

    ChannelInboundResult handle(ChannelInboundEvent event);

    /**
     * Atomically decides whether this handler claims an event and, when it does, handles it.
     * Implementations whose support decision produces a routing snapshot should override this
     * method so mutable bindings are not queried twice for one inbound message.
     */
    default Optional<ChannelInboundResult> tryHandle(ChannelInboundEvent event) {
        return supports(event) ? Optional.of(handle(event)) : Optional.empty();
    }
}

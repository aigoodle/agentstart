package io.aigoodle.channel;

import java.util.List;

public class ChannelInboundDispatcher {
    private final List<ChannelInboundHandler> handlers;

    public ChannelInboundDispatcher(List<ChannelInboundHandler> handlers) {
        this.handlers = handlers == null ? List.of() : List.copyOf(handlers);
    }

    public ChannelInboundResult dispatch(ChannelInboundEvent event) {
        for (ChannelInboundHandler handler : handlers) {
            var result = handler.tryHandle(event);
            if (result.isPresent()) return result.get();
        }
        return ChannelInboundResult.unhandled();
    }
}

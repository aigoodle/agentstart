package io.aigoodle.channel.nativebot;

import io.aigoodle.channel.*;
import io.aigoodle.channel.api.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;

/** Converts native SPI messages and enters the same durable lease/Outbox path as HTTP callbacks. */
final class NativeInboundBridge implements InboundMessageSink {
    private final ObjectProvider<ChannelInboundDispatcher> dispatchers;
    private final ObjectProvider<ChannelEventLogService> eventLogs;

    NativeInboundBridge(
            ObjectProvider<ChannelInboundDispatcher> dispatchers, ObjectProvider<ChannelEventLogService> eventLogs) {
        this.dispatchers = dispatchers;
        this.eventLogs = eventLogs;
    }

    @Override
    public InboundReceipt accept(InboundMessage message) {
        ChannelInboundDispatcher dispatcher = dispatchers.getObject();
        ChannelEventLogService events = eventLogs.getObject();
        ChannelInboundEvent event = convert(message);
        var claim = events.claimInbound(event, "native-stream:" + UUID.randomUUID());
        if (claim.priorResult() != null) return InboundReceipt.acknowledge();
        if (claim.busy()) return new InboundReceipt(false, "busy");
        long started = System.nanoTime();
        try (var ignored = events.keepAlive(claim)) {
            ChannelInboundResult result = dispatcher.dispatch(event);
            events.completeInbound(claim, event, result, (System.nanoTime() - started) / 1_000_000, null);
            return InboundReceipt.acknowledge();
        } catch (RuntimeException failure) {
            events.completeInbound(
                    claim, event, ChannelInboundResult.unhandled(), (System.nanoTime() - started) / 1_000_000, failure);
            throw failure;
        }
    }

    static ChannelInboundEvent convert(InboundMessage message) {
        Map<String, Object> metadata = new LinkedHashMap<>(message.metadata());
        metadata.put("replyTargetId", message.replyTargetId());
        return new ChannelInboundEvent(
                "native",
                message.channelId(),
                message.accountId(),
                message.messageId(),
                message.senderId(),
                message.conversationId(),
                message.text(),
                message.contents().isEmpty()
                        ? "TEXT"
                        : message.contents().getFirst().type().name(),
                List.of(),
                Map.of("conversationType", message.conversationType().name()),
                message.timestamp(),
                message.conversationType() == ConversationType.GROUP,
                metadata);
    }
}

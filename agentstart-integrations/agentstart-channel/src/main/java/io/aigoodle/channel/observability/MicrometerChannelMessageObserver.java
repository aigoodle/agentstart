package io.aigoodle.channel.observability;

import io.aigoodle.channel.ChannelMessageObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;

/** Channel delivery metrics deliberately exclude tenant and user-controlled identifiers. */
public final class MicrometerChannelMessageObserver implements ChannelMessageObserver {
    private final MeterRegistry registry;

    public MicrometerChannelMessageObserver(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void outboundRateLimited(String provider, String channelId, String runtimeNodeId, Duration delay) {
        registry.counter("spring.agent.channel.outbound.rate_limited", tags(provider, channelId, runtimeNodeId))
                .increment();
        registry.timer("spring.agent.channel.outbound.rate_limit_delay", tags(provider, channelId, runtimeNodeId))
                .record(delay);
    }

    @Override
    public void outboundFinished(
            String provider, String channelId, String runtimeNodeId, String outcome, Duration duration) {
        registry.timer(
                        "spring.agent.channel.outbound.delivery",
                        tags(provider, channelId, runtimeNodeId).and("outcome", safe(outcome)))
                .record(duration);
    }

    @Override
    public void conversationProjectionFinished(
            String provider, String channelId, String runtimeNodeId, String outcome) {
        registry.counter(
                        "spring.agent.channel.conversation.projection",
                        tags(provider, channelId, runtimeNodeId).and("outcome", safe(outcome)))
                .increment();
    }

    private static Tags tags(String provider, String channelId, String runtimeNodeId) {
        return Tags.of("provider", safe(provider), "channel", safe(channelId), "runtime", safe(runtimeNodeId));
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}

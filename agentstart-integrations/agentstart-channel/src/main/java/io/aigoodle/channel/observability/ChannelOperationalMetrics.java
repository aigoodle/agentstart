package io.aigoodle.channel.observability;

import io.aigoodle.channel.ChannelOperationalSnapshotProvider;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/** Registers fixed-cardinality deployment gauges backed by a cached database snapshot. */
public final class ChannelOperationalMetrics {
    public ChannelOperationalMetrics(MeterRegistry registry, ChannelOperationalSnapshotProvider provider) {
        gauge(registry, provider, "spring.agent.channel.outbox.pending", s -> s.outboxPending());
        gauge(registry, provider, "spring.agent.channel.outbox.sending", s -> s.outboxSending());
        gauge(registry, provider, "spring.agent.channel.outbox.retrying", s -> s.outboxRetrying());
        gauge(registry, provider, "spring.agent.channel.outbox.dead_letters", s -> s.outboxDeadLetters());
        gauge(registry, provider, "spring.agent.channel.connections.online", s -> s.connectionsOnline());
        gauge(registry, provider, "spring.agent.channel.connections.pending", s -> s.connectionsPending());
        gauge(registry, provider, "spring.agent.channel.connections.error", s -> s.connectionsError());
        gauge(registry, provider, "spring.agent.channel.connections.disabled", s -> s.connectionsDisabled());
    }

    private static void gauge(
            MeterRegistry registry,
            ChannelOperationalSnapshotProvider provider,
            String name,
            java.util.function.ToLongFunction<ChannelOperationalSnapshotProvider.Snapshot> value) {
        Gauge.builder(name, provider, current -> value.applyAsLong(current.snapshot()))
                .register(registry);
    }
}

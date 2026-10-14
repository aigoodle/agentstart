package io.aigoodle.channel.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.channel.ChannelOperationalSnapshotProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ChannelOperationalMetricsTest {
    @Test
    void exposesFixedCardinalityOutboxAndConnectionGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        var snapshot = new ChannelOperationalSnapshotProvider.Snapshot(3, 2, 4, 1, 8, 5, 2, 6);
        new ChannelOperationalMetrics(registry, () -> snapshot);

        assertThat(registry.get("spring.agent.channel.outbox.pending").gauge().value())
                .isEqualTo(3);
        assertThat(registry.get("spring.agent.channel.outbox.dead_letters")
                        .gauge()
                        .value())
                .isEqualTo(1);
        assertThat(registry.get("spring.agent.channel.connections.online")
                        .gauge()
                        .value())
                .isEqualTo(8);
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags()).isEmpty());
    }
}

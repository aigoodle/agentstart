package io.aigoodle.channel.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MicrometerChannelMessageObserverTest {
    @Test
    void recordsDeliveryAndRateLimitWithoutTenantOrAccountTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        var observer = new MicrometerChannelMessageObserver(registry);

        observer.outboundRateLimited("openclaw", "qqbot", "node-a", Duration.ofMillis(250));
        observer.outboundFinished("openclaw", "qqbot", "node-a", "success", Duration.ofMillis(80));
        observer.conversationProjectionFinished("openclaw", "qqbot", "node-a", "failure");

        assertThat(registry.get("spring.agent.channel.outbound.rate_limited")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get("spring.agent.channel.outbound.delivery")
                        .tag("outcome", "success")
                        .timer()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get("spring.agent.channel.conversation.projection")
                        .tag("outcome", "failure")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .extracting(tag -> tag.getKey())
                        .doesNotContain("tenant", "account", "conversation", "message"));
    }
}

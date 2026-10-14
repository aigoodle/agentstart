package io.aigoodle.channel.observability;

import io.aigoodle.channel.ChannelOperationalSnapshotProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(
        afterName = {
            "io.aigoodle.channel.config.GoodleChannelAutoConfiguration",
            "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
            "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration"
        })
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(
        prefix = "spring-agent.observability",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ChannelMetricsAutoConfiguration {
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(MicrometerChannelMessageObserver.class)
    public MicrometerChannelMessageObserver micrometerChannelMessageObserver(MeterRegistry registry) {
        return new MicrometerChannelMessageObserver(registry);
    }

    @Bean
    @ConditionalOnBean({MeterRegistry.class, ChannelOperationalSnapshotProvider.class})
    @ConditionalOnMissingBean(ChannelOperationalMetrics.class)
    public ChannelOperationalMetrics channelOperationalMetrics(
            MeterRegistry registry, ChannelOperationalSnapshotProvider provider) {
        return new ChannelOperationalMetrics(registry, provider);
    }
}

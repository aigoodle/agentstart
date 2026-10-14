package io.aigoodle.tool.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(
        afterName = {
            "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
            "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration"
        })
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(
        prefix = "spring-agent.observability",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ToolMetricsAutoConfiguration {
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(MicrometerToolExecutionListener.class)
    public MicrometerToolExecutionListener micrometerToolExecutionListener(MeterRegistry registry) {
        return new MicrometerToolExecutionListener(registry);
    }
}

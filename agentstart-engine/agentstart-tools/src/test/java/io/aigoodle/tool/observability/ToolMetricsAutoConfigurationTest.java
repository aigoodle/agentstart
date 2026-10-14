package io.aigoodle.tool.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ToolMetricsAutoConfigurationTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolMetricsAutoConfiguration.class));

    @Test
    void registersMetricsOnlyWhenRegistryIsPresentAndObservationEnabled() {
        runner.run(context -> assertThat(context).doesNotHaveBean(MicrometerToolExecutionListener.class));
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> assertThat(context).hasSingleBean(MicrometerToolExecutionListener.class));
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("spring-agent.observability.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MicrometerToolExecutionListener.class));
    }

    @Test
    void startsWithoutOptionalMicrometerDependency() {
        runner.withClassLoader(new FilteredClassLoader("io.micrometer"))
                .run(context -> assertThat(context).hasNotFailed());
    }
}

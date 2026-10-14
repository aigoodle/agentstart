package io.aigoodle.tool.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.tool.execution.ToolExecutionContext;
import io.aigoodle.tool.execution.ToolExecutionRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolMetricsTest {
    @Test
    void recordsGovernedToolStatusDurationAndAttempts() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        var listener = new MicrometerToolExecutionListener(registry);
        listener.onExecution(new ToolExecutionRecord(
                "run-1",
                "search",
                ToolExecutionRecord.Status.SUCCEEDED,
                2,
                Duration.ofMillis(12),
                null,
                new ToolExecutionContext("run-1", "default", "agent-1", "c1", Map.of())));

        assertThat(registry.get("spring.agent.tools.executions")
                        .tag("tool", "search")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get("spring.agent.tools.attempts").summary().totalAmount())
                .isEqualTo(2);
    }
}

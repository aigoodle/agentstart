package io.aigoodle.tool.observability;

import io.aigoodle.tool.execution.ToolExecutionListener;
import io.aigoodle.tool.execution.ToolExecutionRecord;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.concurrent.TimeUnit;

/** Low-cardinality tool execution metrics emitted from the shared gateway. */
public final class MicrometerToolExecutionListener implements ToolExecutionListener {

    private final MeterRegistry registry;

    public MicrometerToolExecutionListener(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onExecution(ToolExecutionRecord record) {
        Tags tags = Tags.of(
                "tool",
                safe(record.toolName()),
                "status",
                record.status().name().toLowerCase());
        registry.counter("spring.agent.tools.executions", tags).increment();
        registry.timer("spring.agent.tools.duration", tags)
                .record(record.duration().toNanos(), TimeUnit.NANOSECONDS);
        registry.summary("spring.agent.tools.attempts", tags).record(record.attempts());
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}

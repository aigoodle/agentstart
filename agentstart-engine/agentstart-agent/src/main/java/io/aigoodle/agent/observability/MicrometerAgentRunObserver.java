package io.aigoodle.agent.observability;

import io.aigoodle.agent.runtime.AgentRunObservation;
import io.aigoodle.agent.runtime.AgentRunObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.concurrent.TimeUnit;

/** Micrometer adapter for agent lifecycle signals; run/conversation IDs stay out of metric tags. */
public final class MicrometerAgentRunObserver implements AgentRunObserver {

    private final MeterRegistry registry;

    public MicrometerAgentRunObserver(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onStarted(AgentRunObservation observation) {
        registry.counter("spring.agent.runs.started", baseTags(observation)).increment();
    }

    @Override
    public void onFinished(AgentRunObservation observation) {
        Tags tags =
                baseTags(observation).and("status", observation.status().name().toLowerCase());
        registry.counter("spring.agent.runs.finished", tags).increment();
        registry.timer("spring.agent.runs.duration", tags)
                .record(observation.duration().toNanos(), TimeUnit.NANOSECONDS);
    }

    private static Tags baseTags(AgentRunObservation value) {
        return Tags.of(
                "agent",
                safe(value.agentId()),
                "strategy",
                value.strategy() == null ? "unknown" : value.strategy().name().toLowerCase(),
                "resumed",
                Boolean.toString(value.resumed()));
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}

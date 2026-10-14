package io.aigoodle.agent.observability;

import io.aigoodle.agent.runtime.AgentRunObservation;
import io.aigoodle.agent.runtime.AgentRunObserver;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.ConcurrentHashMap;

/** Creates a run observation that can be exported as OpenTelemetry spans by the host bridge. */
public final class ObservationAgentRunObserver implements AgentRunObserver {

    private final ObservationRegistry registry;
    private final ConcurrentHashMap<String, ActiveObservation> active = new ConcurrentHashMap<>();

    public ObservationAgentRunObserver(ObservationRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onStarted(AgentRunObservation signal) {
        Observation observation = Observation.createNotStarted("spring.agent.run", registry)
                .lowCardinalityKeyValue("agent", safe(signal.agentId()))
                .lowCardinalityKeyValue(
                        "strategy",
                        signal.strategy() == null
                                ? "unknown"
                                : signal.strategy().name().toLowerCase())
                .lowCardinalityKeyValue("resumed", Boolean.toString(signal.resumed()))
                .highCardinalityKeyValue("run.id", safe(signal.runId()))
                .highCardinalityKeyValue("conversation.id", safe(signal.conversationId()))
                .start();
        ActiveObservation previous =
                active.put(key(signal), new ActiveObservation(observation, observation.openScope()));
        if (previous != null) previous.close();
    }

    @Override
    public void onFinished(AgentRunObservation signal) {
        ActiveObservation current = active.remove(key(signal));
        if (current == null) return;
        current.observation.lowCardinalityKeyValue(
                "status", signal.status().name().toLowerCase());
        if (signal.error() != null && !signal.error().isBlank()) {
            current.observation.error(new IllegalStateException(signal.error()));
        }
        current.close();
    }

    private static String key(AgentRunObservation signal) {
        return safe(signal.tenantId()) + ':' + safe(signal.runId()) + ':' + signal.resumed();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private record ActiveObservation(Observation observation, Observation.Scope scope) {
        private void close() {
            try {
                scope.close();
            } finally {
                observation.stop();
            }
        }
    }
}

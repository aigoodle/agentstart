package io.aigoodle.agent.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.agent.api.AgentStrategyType;
import io.aigoodle.agent.runtime.AgentRunObservation;
import io.aigoodle.agent.runtime.AgentRunStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AgentOpsMetricsTest {

    @Test
    void recordsAgentLifecycleWithoutHighCardinalityRunTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        var observer = new MicrometerAgentRunObserver(registry);
        var observation = new AgentRunObservation(
                "run-secret",
                "tenant",
                "agent-1",
                "conversation",
                AgentStrategyType.REACT,
                false,
                AgentRunStatus.COMPLETED,
                Instant.now(),
                Duration.ofMillis(25),
                null);

        observer.onStarted(observation);
        observer.onFinished(observation);

        assertThat(registry.get("spring.agent.runs.started").counter().count()).isEqualTo(1);
        assertThat(registry.get("spring.agent.runs.finished")
                        .tag("status", "completed")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTag("run_id")).isNull());
    }

    @Test
    void exposesRunAsObservationForOpenTelemetryBridges() {
        ObservationRegistry registry = ObservationRegistry.create();
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            public void onStart(Observation.Context context) {
                starts.incrementAndGet();
            }

            public void onStop(Observation.Context context) {
                stops.incrementAndGet();
            }

            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
        var observer = new ObservationAgentRunObserver(registry);
        var signal = new AgentRunObservation(
                "run-1",
                "tenant",
                "agent-1",
                "conversation",
                AgentStrategyType.REACT,
                false,
                AgentRunStatus.COMPLETED,
                Instant.now(),
                Duration.ofMillis(4),
                null);

        observer.onStarted(signal);
        assertThat(registry.getCurrentObservation()).isNotNull();
        observer.onFinished(signal);

        assertThat(starts).hasValue(1);
        assertThat(stops).hasValue(1);
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void activeObservationsAreIsolatedWhenTwoTenantsReuseARunId() {
        ObservationRegistry registry = ObservationRegistry.create();
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            public void onStart(Observation.Context context) {
                starts.incrementAndGet();
            }

            public void onStop(Observation.Context context) {
                stops.incrementAndGet();
            }

            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
        var observer = new ObservationAgentRunObserver(registry);
        var tenantA = signal("shared-run", "tenant-a");
        var tenantB = signal("shared-run", "tenant-b");

        observer.onStarted(tenantA);
        observer.onStarted(tenantB);
        assertThat(starts).hasValue(2);
        assertThat(stops).hasValue(0);

        observer.onFinished(tenantA);
        assertThat(stops).hasValue(1);
        observer.onFinished(tenantB);
        assertThat(stops).hasValue(2);
    }

    private static AgentRunObservation signal(String runId, String tenantId) {
        return new AgentRunObservation(
                runId,
                tenantId,
                "agent-1",
                "conversation",
                AgentStrategyType.REACT,
                false,
                AgentRunStatus.COMPLETED,
                Instant.now(),
                Duration.ofMillis(4),
                null);
    }
}

package io.aigoodle.workflow.service;

import io.aigoodle.workflow.engine.NodeExecutionStatus;
import io.aigoodle.workflow.engine.WorkflowRunStatus;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.NodeResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;

/** Micrometer implementation with bounded tag values suitable for production backends. */
public final class MicrometerWorkflowRuntimeMetrics implements WorkflowRuntimeMetrics {
    private final MeterRegistry registry;

    public MicrometerWorkflowRuntimeMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void runStarted() {
        registry.counter("goodle.workflow.runs.started").increment();
    }

    @Override
    public void runFinished(WorkflowRunStatus status, long elapsedNanos) {
        registry.counter("goodle.workflow.runs.finished", "status", status.name())
                .increment();
        Timer.builder("goodle.workflow.run.duration")
                .tag("status", status.name())
                .register(registry)
                .record(Duration.ofNanos(elapsedNanos));
    }

    @Override
    public void nodeFinished(NodeType type, NodeExecutionStatus status, NodeResult result) {
        String code = result.getErrorCode() == null ? "none" : bounded(result.getErrorCode());
        registry.counter(
                        "goodle.workflow.nodes.finished",
                        "type",
                        type.name(),
                        "status",
                        status.name(),
                        "error_code",
                        code)
                .increment();
        if (status == NodeExecutionStatus.RETRYING) {
            registry.counter("goodle.workflow.node.retries", "type", type.name(), "error_code", code)
                    .increment();
        }
    }

    @Override
    public void recovery(String outcome) {
        registry.counter("goodle.workflow.recovery", "outcome", bounded(outcome))
                .increment();
    }

    @Override
    public void leaseLost() {
        registry.counter("goodle.workflow.lease.lost").increment();
    }

    private static String bounded(String value) {
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
}

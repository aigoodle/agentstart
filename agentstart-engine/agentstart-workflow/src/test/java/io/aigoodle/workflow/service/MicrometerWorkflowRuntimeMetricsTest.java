package io.aigoodle.workflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.aigoodle.workflow.engine.NodeExecutionStatus;
import io.aigoodle.workflow.engine.WorkflowRunStatus;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.NodeResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MicrometerWorkflowRuntimeMetricsTest {

    @Test
    void publishesBoundedOperationalMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WorkflowRuntimeMetrics metrics = new MicrometerWorkflowRuntimeMetrics(registry);

        metrics.runStarted();
        metrics.nodeFinished(
                NodeType.HTTP_REQUEST,
                NodeExecutionStatus.RETRYING,
                NodeResult.transientFailure("HTTP_503", "unavailable"));
        metrics.runFinished(WorkflowRunStatus.SUCCEEDED, 1_000_000);
        metrics.recovery("recovered");
        metrics.leaseLost();

        assertEquals(1, registry.get("goodle.workflow.runs.started").counter().count());
        assertEquals(
                1,
                registry.get("goodle.workflow.node.retries")
                        .tag("error_code", "HTTP_503")
                        .counter()
                        .count());
        assertEquals(
                1,
                registry.get("goodle.workflow.runs.finished")
                        .tag("status", "SUCCEEDED")
                        .counter()
                        .count());
        assertEquals(
                1,
                registry.get("goodle.workflow.recovery")
                        .tag("outcome", "recovered")
                        .counter()
                        .count());
        assertEquals(1, registry.get("goodle.workflow.lease.lost").counter().count());
    }
}

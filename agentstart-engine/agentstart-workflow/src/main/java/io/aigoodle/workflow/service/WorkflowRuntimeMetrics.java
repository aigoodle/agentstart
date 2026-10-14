package io.aigoodle.workflow.service;

import io.aigoodle.workflow.engine.NodeExecutionStatus;
import io.aigoodle.workflow.engine.WorkflowRunStatus;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.NodeResult;

/** Low-cardinality production metrics; NOOP keeps observability optional. */
public interface WorkflowRuntimeMetrics {
    WorkflowRuntimeMetrics NOOP = new WorkflowRuntimeMetrics() {};

    default void runStarted() {}

    default void runFinished(WorkflowRunStatus status, long elapsedNanos) {}

    default void nodeFinished(NodeType type, NodeExecutionStatus status, NodeResult result) {}

    default void recovery(String outcome) {}

    default void leaseLost() {}
}

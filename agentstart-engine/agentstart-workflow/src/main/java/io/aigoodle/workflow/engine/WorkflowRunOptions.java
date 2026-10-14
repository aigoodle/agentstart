package io.aigoodle.workflow.engine;

import java.time.Duration;

/** Resource and deadline policy for one execution. */
public record WorkflowRunOptions(
        Duration workflowTimeout,
        Duration defaultNodeTimeout,
        int maxConcurrency,
        RunCancellationToken cancellationToken,
        String runId,
        String resourceTenantId,
        String appId) {

    public WorkflowRunOptions(
            Duration workflowTimeout,
            Duration defaultNodeTimeout,
            int maxConcurrency,
            RunCancellationToken cancellationToken,
            String runId,
            String resourceTenantId) {
        this(workflowTimeout, defaultNodeTimeout, maxConcurrency, cancellationToken, runId, resourceTenantId, null);
    }

    public WorkflowRunOptions(
            Duration workflowTimeout,
            Duration defaultNodeTimeout,
            int maxConcurrency,
            RunCancellationToken cancellationToken,
            String runId) {
        this(workflowTimeout, defaultNodeTimeout, maxConcurrency, cancellationToken, runId, null);
    }

    public WorkflowRunOptions(
            Duration workflowTimeout,
            Duration defaultNodeTimeout,
            int maxConcurrency,
            RunCancellationToken cancellationToken) {
        this(workflowTimeout, defaultNodeTimeout, maxConcurrency, cancellationToken, null);
    }

    public WorkflowRunOptions {
        if (workflowTimeout == null || workflowTimeout.isNegative() || workflowTimeout.isZero())
            throw new IllegalArgumentException("workflowTimeout must be positive");
        if (defaultNodeTimeout == null || defaultNodeTimeout.isNegative() || defaultNodeTimeout.isZero())
            throw new IllegalArgumentException("defaultNodeTimeout must be positive");
        if (maxConcurrency < 1) throw new IllegalArgumentException("maxConcurrency must be positive");
        if (cancellationToken == null) cancellationToken = new RunCancellationToken();
    }

    public static WorkflowRunOptions defaults() {
        return new WorkflowRunOptions(Duration.ofMinutes(5), Duration.ofMinutes(1), 64, new RunCancellationToken());
    }

    public WorkflowRunOptions withRunId(String value) {
        return new WorkflowRunOptions(
                workflowTimeout, defaultNodeTimeout, maxConcurrency, cancellationToken, value, resourceTenantId, appId);
    }

    public WorkflowRunOptions withAppId(String value) {
        return new WorkflowRunOptions(
                workflowTimeout, defaultNodeTimeout, maxConcurrency, cancellationToken, runId, resourceTenantId, value);
    }

    public WorkflowRunOptions withResourceTenantId(String value) {
        return new WorkflowRunOptions(
                workflowTimeout, defaultNodeTimeout, maxConcurrency, cancellationToken, runId, value, appId);
    }
}

package io.aigoodle.trigger.api;

/**
 * Lifecycle of one trigger invocation.
 */
public enum InvocationStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

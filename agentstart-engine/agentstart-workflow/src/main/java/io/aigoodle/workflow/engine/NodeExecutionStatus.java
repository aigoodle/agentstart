package io.aigoodle.workflow.engine;

public enum NodeExecutionStatus {
    PENDING,
    SCHEDULED,
    RUNNING,
    RETRYING,
    COMPLETED,
    FAILED,
    SKIPPED,
    WAITING,
    CANCELLED
}

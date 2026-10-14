package io.aigoodle.workflow.engine;

public enum WorkflowExecutionEventType {
    NODE_SCHEDULED,
    NODE_STARTED,
    NODE_OUTPUT,
    NODE_RETRYING,
    NODE_COMPLETED,
    NODE_FAILED,
    RUN_STARTED,
    RUN_WAITING,
    RUN_RESUMED,
    RUN_PAUSING,
    RUN_PAUSED,
    RUN_CANCELLING,
    RUN_CANCELLED,
    RUN_TIMED_OUT,
    RUN_COMPLETED,
    RUN_FAILED
}

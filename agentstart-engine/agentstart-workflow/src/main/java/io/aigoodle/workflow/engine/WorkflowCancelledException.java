package io.aigoodle.workflow.engine;

/** Raised at cooperative cancellation boundaries inside node executors. */
public final class WorkflowCancelledException extends RuntimeException {
    private final boolean timedOut;

    public WorkflowCancelledException(String message, boolean timedOut) {
        super(message);
        this.timedOut = timedOut;
    }

    public boolean isTimedOut() {
        return timedOut;
    }
}

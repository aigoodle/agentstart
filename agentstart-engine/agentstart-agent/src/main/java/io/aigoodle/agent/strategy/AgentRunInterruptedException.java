package io.aigoodle.agent.strategy;

/** Signals cooperative cancellation or deadline expiry at an execution boundary. */
public final class AgentRunInterruptedException extends RuntimeException {

    private final boolean timedOut;

    private AgentRunInterruptedException(String message, boolean timedOut) {
        super(message);
        this.timedOut = timedOut;
    }

    public static AgentRunInterruptedException cancelled(String runId) {
        return new AgentRunInterruptedException("Agent run cancelled: " + runId, false);
    }

    public static AgentRunInterruptedException timedOut(String runId) {
        return new AgentRunInterruptedException("Agent run timed out: " + runId, true);
    }

    public boolean isTimedOut() {
        return timedOut;
    }
}

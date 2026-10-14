package io.aigoodle.agent.runtime;

/** Optional AgentOps hook; observer failures never affect the user run. */
public interface AgentRunObserver {
    default void onStarted(AgentRunObservation observation) {}

    default void onFinished(AgentRunObservation observation) {}
}

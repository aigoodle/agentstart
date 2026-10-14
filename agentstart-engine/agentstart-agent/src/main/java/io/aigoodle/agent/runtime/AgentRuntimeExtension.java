package io.aigoodle.agent.runtime;

/** Optional named execution backend discovered from the host Spring context. */
public interface AgentRuntimeExtension extends AgentRuntime {
    String runtimeType();
}

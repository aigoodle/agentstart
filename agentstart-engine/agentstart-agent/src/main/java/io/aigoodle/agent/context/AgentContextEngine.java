package io.aigoodle.agent.context;

/** Replaceable boundary for memory retrieval, prioritisation and prompt-budget control. */
@FunctionalInterface
public interface AgentContextEngine {
    AgentContextWindow assemble(AgentContextRequest request);
}

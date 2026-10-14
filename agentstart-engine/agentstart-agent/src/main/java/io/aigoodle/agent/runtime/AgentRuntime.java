package io.aigoodle.agent.runtime;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.api.AgentStep;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Persistence-independent public execution facade of {@code agentstart-agent}.
 * Workflow and other orchestrators depend on this contract, never on agent CRUD.
 */
public interface AgentRuntime {
    default AgentResponse run(AgentDefinition definition, AgentRequest request) {
        return run(definition, request, null, null);
    }

    default AgentResponse run(AgentDefinition definition, AgentRequest request, Consumer<AgentStep> stepListener) {
        return run(definition, request, stepListener, null);
    }

    AgentResponse run(
            AgentDefinition definition,
            AgentRequest request,
            Consumer<AgentStep> stepListener,
            Consumer<String> tokenListener);

    /** Inspect a run without coupling callers to the persistence implementation. */
    default Optional<AgentRunSnapshot> findRun(String runId) {
        return Optional.empty();
    }

    /** Read an ordered slice of the run event stream for UI, audit or tracing adapters. */
    default List<AgentRunEvent> runEvents(String runId, long afterSequence, int limit) {
        return List.of();
    }

    /** Continue a run from its exact persisted strategy checkpoint. */
    default AgentResponse resume(String runId, AgentResumeCommand command) {
        throw new UnsupportedOperationException("This AgentRuntime does not support resume");
    }

    /** Cancel a non-terminal run. Returns the resulting durable snapshot. */
    default AgentRunSnapshot cancel(String runId) {
        throw new UnsupportedOperationException("This AgentRuntime does not support cancellation");
    }
}

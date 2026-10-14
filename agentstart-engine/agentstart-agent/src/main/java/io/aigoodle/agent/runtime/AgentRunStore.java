package io.aigoodle.agent.runtime;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import java.util.List;
import java.util.Optional;

/** Persistence SPI for executions and their append-only event stream. */
public interface AgentRunStore {

    AgentRunSnapshot create(String runId, AgentDefinition definition, AgentRequest request, String conversationId);

    AgentRunSnapshot transition(String runId, AgentRunStatus target, AgentResponse response, String error);

    /** Tenant-explicit variant for async/runtime calls that already carry trusted identity. */
    default AgentRunSnapshot transition(
            String tenantId, String runId, AgentRunStatus target, AgentResponse response, String error) {
        return transition(runId, target, response, error);
    }

    Optional<AgentRunSnapshot> find(String runId);

    /** Tenant-explicit variant; persistent stores must constrain the lookup by this tenant. */
    default Optional<AgentRunSnapshot> find(String tenantId, String runId) {
        return find(runId);
    }

    List<AgentRunEvent> events(String runId, long afterSequence, int limit);

    default List<AgentRunEvent> events(String tenantId, String runId, long afterSequence, int limit) {
        return events(runId, afterSequence, limit);
    }

    /** Append a domain event without changing the run lifecycle status. */
    void appendEvent(String runId, String type, String payloadJson);

    default void appendEvent(String tenantId, String runId, String type, String payloadJson) {
        appendEvent(runId, type, payloadJson);
    }
}

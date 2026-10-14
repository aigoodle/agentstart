package io.aigoodle.agent.context;

import io.aigoodle.agent.api.AgentDefinition;

/** Inputs needed to assemble a bounded, model-ready agent context. */
public record AgentContextRequest(
        AgentDefinition definition, String memoryOwnerId, String conversationId, String query, int maxCharacters) {
    public AgentContextRequest(AgentDefinition definition, String conversationId, String query, int maxCharacters) {
        this(definition, definition == null ? null : definition.getId(), conversationId, query, maxCharacters);
    }
}

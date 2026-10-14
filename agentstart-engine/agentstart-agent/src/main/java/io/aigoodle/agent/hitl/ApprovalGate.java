package io.aigoodle.agent.hitl;

/**
 * Human-in-the-loop checkpoint consulted before an agent executes a tool flagged as
 * requiring approval. Implementations decide synchronously (auto policy, rules engine)
 * or signal that a human must decide out-of-band ({@link Decision#PENDING}).
 */
public interface ApprovalGate {

    enum Decision {
        APPROVE,
        DENY,
        PENDING
    }

    Decision review(ToolCall toolCall);

    record ToolCall(String agentId, String conversationId, String toolName, String toolInput) {}
}

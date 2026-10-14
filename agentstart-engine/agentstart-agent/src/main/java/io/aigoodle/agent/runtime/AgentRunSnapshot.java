package io.aigoodle.agent.runtime;

import java.time.LocalDateTime;

/** Read-only, transport-safe view of a durable agent run. */
public record AgentRunSnapshot(
        String runId,
        String tenantId,
        String agentId,
        String conversationId,
        AgentRunStatus status,
        String definitionJson,
        String requestJson,
        String responseJson,
        String error,
        long version,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}

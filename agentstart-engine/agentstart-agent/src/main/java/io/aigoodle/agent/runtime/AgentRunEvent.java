package io.aigoodle.agent.runtime;

import java.time.LocalDateTime;

/** Append-only lifecycle event suitable for SSE, tracing and replay projections. */
public record AgentRunEvent(
        String eventId, String runId, long sequence, String type, String payloadJson, LocalDateTime createdAt) {}

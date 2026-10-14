package io.aigoodle.agent.runtime;

import io.aigoodle.agent.api.AgentStrategyType;
import java.time.Duration;
import java.time.Instant;

/** Low-cardinality lifecycle signal exposed to metrics/tracing adapters. */
public record AgentRunObservation(
        String runId,
        String tenantId,
        String agentId,
        String conversationId,
        AgentStrategyType strategy,
        boolean resumed,
        AgentRunStatus status,
        Instant startedAt,
        Duration duration,
        String error) {}

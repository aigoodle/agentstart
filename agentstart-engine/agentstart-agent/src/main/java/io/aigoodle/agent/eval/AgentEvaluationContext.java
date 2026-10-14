package io.aigoodle.agent.eval;

import io.aigoodle.agent.api.AgentResponse;
import java.time.Duration;

/** Actual execution supplied to deterministic or model-based evaluators. */
public record AgentEvaluationContext(
        AgentEvaluationCase testCase, AgentResponse response, Duration duration, Throwable error) {}

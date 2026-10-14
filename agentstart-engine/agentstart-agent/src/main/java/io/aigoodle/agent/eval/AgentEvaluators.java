package io.aigoodle.agent.eval;

import io.aigoodle.agent.api.AgentResponse;
import java.time.Duration;
import java.util.Locale;

/** Built-in deterministic judges suitable for CI; semantic judges can implement the SPI. */
public final class AgentEvaluators {
    private AgentEvaluators() {}

    public static AgentEvaluator contains(String expected) {
        return context -> {
            String actual = context.response() == null ? "" : context.response().getText();
            boolean matches = actual != null
                    && expected != null
                    && actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
            return new AgentEvaluationScore(
                    "contains",
                    matches ? 1 : 0,
                    matches ? "Expected content found" : "Missing expected content: " + expected);
        };
    }

    public static AgentEvaluator completed() {
        return context -> new AgentEvaluationScore(
                "completed",
                context.response() != null && context.response().getStatus() == AgentResponse.Status.COMPLETED ? 1 : 0,
                context.response() == null
                        ? "No response"
                        : "Status: " + context.response().getStatus());
    }

    public static AgentEvaluator maxLatency(Duration maximum) {
        return context -> {
            boolean within = maximum != null && context.duration().compareTo(maximum) <= 0;
            return new AgentEvaluationScore(
                    "max-latency", within ? 1 : 0, "Actual " + context.duration() + ", maximum " + maximum);
        };
    }
}

package io.aigoodle.workflow.node;

import io.aigoodle.workflow.graph.NodeDef;
import java.time.Duration;

/** Deterministic bounded exponential retry policy. */
public record NodeRetryPolicy(int maxAttempts, Duration initialBackoff, double multiplier, Duration maxBackoff) {
    public NodeRetryPolicy {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        if (initialBackoff == null || initialBackoff.isNegative()) initialBackoff = Duration.ZERO;
        if (multiplier < 1) multiplier = 1;
        if (maxBackoff == null || maxBackoff.isNegative()) maxBackoff = initialBackoff;
    }

    public static NodeRetryPolicy from(NodeDef node) {
        int maxAttempts = Math.max(1, node.getInt("maxAttempts", 1));
        long initial = Math.max(0, node.getInt("retryBackoffMillis", 0));
        long maximum = Math.max(initial, node.getInt("maxRetryBackoffMillis", (int) initial));
        Object configuredMultiplier = node.get("retryBackoffMultiplier");
        double multiplier = configuredMultiplier instanceof Number number ? number.doubleValue() : 2.0;
        return new NodeRetryPolicy(maxAttempts, Duration.ofMillis(initial), multiplier, Duration.ofMillis(maximum));
    }

    public Duration backoffBefore(int nextAttempt) {
        if (nextAttempt <= 1 || initialBackoff.isZero()) return Duration.ZERO;
        double value = initialBackoff.toMillis() * Math.pow(multiplier, nextAttempt - 2);
        return Duration.ofMillis(Math.min(maxBackoff.toMillis(), (long) value));
    }
}

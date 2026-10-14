package io.aigoodle.observability.api;

import java.time.Duration;

/** Fixed monitoring windows and bucket sizes used by the LLMOps trend chart. */
public enum LlmTrendRange {
    HOUR(Duration.ofHours(1), Duration.ofMinutes(10), 6),
    DAY(Duration.ofHours(24), Duration.ofHours(1), 24),
    WEEK(Duration.ofDays(7), Duration.ofDays(1), 7);

    private final Duration duration;
    private final Duration bucketSize;
    private final int bucketCount;

    LlmTrendRange(Duration duration, Duration bucketSize, int bucketCount) {
        this.duration = duration;
        this.bucketSize = bucketSize;
        this.bucketCount = bucketCount;
    }

    public Duration duration() {
        return duration;
    }

    public Duration bucketSize() {
        return bucketSize;
    }

    public int bucketCount() {
        return bucketCount;
    }
}

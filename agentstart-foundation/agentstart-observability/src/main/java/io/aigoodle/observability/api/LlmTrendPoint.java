package io.aigoodle.observability.api;

import java.time.LocalDateTime;

/** One zero-filled time bucket in an LLM invocation trend. */
public record LlmTrendPoint(
        LocalDateTime bucketStart, long calls, long errors, long totalTokens, long costMicros, double avgLatencyMs) {}

package io.aigoodle.observability.api;

import lombok.Builder;
import lombok.Data;

/**
 * Aggregated LLM usage, e.g. per model or per tenant — the shape an LLMOps dashboard
 * consumes.
 */
@Data
@Builder
public class LlmUsageStats {

    private String model;
    private long calls;
    private long errors;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private long costMicros;
    private double avgLatencyMs;

    public double costAmount() {
        return costMicros / 1_000_000.0;
    }
}

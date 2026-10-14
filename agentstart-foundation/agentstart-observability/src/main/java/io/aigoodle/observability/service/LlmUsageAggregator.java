package io.aigoodle.observability.service;

import io.aigoodle.observability.api.LlmUsageStats;
import io.aigoodle.observability.entity.LlmCallRecord;
import java.util.List;

/** Accumulates persisted model-call facts into dashboard statistics. */
final class LlmUsageAggregator {

    LlmUsageStats aggregate(String model, List<LlmCallRecord> records) {
        UsageTotals totals = new UsageTotals();
        records.forEach(totals::include);
        return totals.toStats(model);
    }

    private static final class UsageTotals {

        private long calls;
        private long errors;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private long costMicros;
        private long latencyMs;

        void include(LlmCallRecord record) {
            calls++;
            if (!Boolean.TRUE.equals(record.getSuccess())) {
                errors++;
            }
            promptTokens += valueOrZero(record.getPromptTokens());
            completionTokens += valueOrZero(record.getCompletionTokens());
            totalTokens += valueOrZero(record.getTotalTokens());
            costMicros += valueOrZero(record.getCostMicros());
            latencyMs += valueOrZero(record.getLatencyMs());
        }

        LlmUsageStats toStats(String model) {
            return LlmUsageStats.builder()
                    .model(model)
                    .calls(calls)
                    .errors(errors)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .costMicros(costMicros)
                    .avgLatencyMs(calls == 0 ? 0.0 : (double) latencyMs / calls)
                    .build();
        }

        private static long valueOrZero(Number value) {
            return value == null ? 0 : value.longValue();
        }
    }
}

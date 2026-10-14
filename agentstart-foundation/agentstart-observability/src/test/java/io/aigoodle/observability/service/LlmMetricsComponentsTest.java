package io.aigoodle.observability.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.observability.api.LlmCallMeasurement;
import io.aigoodle.observability.api.LlmUsageStats;
import io.aigoodle.observability.api.ModelCallContext;
import io.aigoodle.observability.api.TokenUsage;
import io.aigoodle.observability.entity.LlmCallRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmMetricsComponentsTest {

    @Test
    void createsAPersistenceRecordFromOneCoherentMeasurement() {
        LlmCallRecordFactory factory = new LlmCallRecordFactory((model, usage) -> usage.totalTokens() * 2L);

        LlmCallRecord record = factory.create(
                LlmCallMeasurement.successful("openai", "gpt-4o", "tenant-a", new TokenUsage(120, 30, 150), 85));

        assertThat(record)
                .extracting(
                        LlmCallRecord::getProvider,
                        LlmCallRecord::getModel,
                        LlmCallRecord::getTenantId,
                        LlmCallRecord::getPromptTokens,
                        LlmCallRecord::getCompletionTokens,
                        LlmCallRecord::getTotalTokens,
                        LlmCallRecord::getCostMicros,
                        LlmCallRecord::getLatencyMs,
                        LlmCallRecord::getSuccess)
                .containsExactly("openai", "gpt-4o", "tenant-a", 120, 30, 150, 300L, 85L, true);
    }

    @Test
    void aggregatesNullablePersistenceValuesWithoutLeakingStorageDetails() {
        LlmCallRecord successfulCall = record(true, 10, 4, 14, 25L, 80L);
        LlmCallRecord failedCall = record(false, null, null, null, null, null);

        LlmUsageStats stats = new LlmUsageAggregator().aggregate("gpt-4o", List.of(successfulCall, failedCall));

        assertThat(stats)
                .extracting(
                        LlmUsageStats::getModel,
                        LlmUsageStats::getCalls,
                        LlmUsageStats::getErrors,
                        LlmUsageStats::getPromptTokens,
                        LlmUsageStats::getCompletionTokens,
                        LlmUsageStats::getTotalTokens,
                        LlmUsageStats::getCostMicros,
                        LlmUsageStats::getAvgLatencyMs)
                .containsExactly("gpt-4o", 2L, 1L, 10L, 4L, 14L, 25L, 40.0);
    }

    @Test
    void normalizesMissingUsageAtTheMeasurementBoundary() {
        LlmCallMeasurement measurement =
                LlmCallMeasurement.successful(ModelCallContext.of("provider", "model"), null, 10);

        assertThat(measurement.tokenUsage()).isEqualTo(TokenUsage.ZERO);
    }

    @Test
    void keepsModelIdentityTogetherWhenCreatingAMeasurement() {
        ModelCallContext callContext = new ModelCallContext("openai", "gpt-4o", "tenant-a");

        LlmCallMeasurement measurement = LlmCallMeasurement.failed(callContext, 42, "TimeoutException");

        assertThat(measurement)
                .extracting(
                        LlmCallMeasurement::provider,
                        LlmCallMeasurement::model,
                        LlmCallMeasurement::tenantId,
                        LlmCallMeasurement::latencyMs,
                        LlmCallMeasurement::successful,
                        LlmCallMeasurement::errorType)
                .containsExactly("openai", "gpt-4o", "tenant-a", 42L, false, "TimeoutException");
    }

    private static LlmCallRecord record(
            boolean successful,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Long costMicros,
            Long latencyMs) {
        LlmCallRecord record = new LlmCallRecord();
        record.setSuccess(successful);
        record.setPromptTokens(promptTokens);
        record.setCompletionTokens(completionTokens);
        record.setTotalTokens(totalTokens);
        record.setCostMicros(costMicros);
        record.setLatencyMs(latencyMs);
        return record;
    }
}

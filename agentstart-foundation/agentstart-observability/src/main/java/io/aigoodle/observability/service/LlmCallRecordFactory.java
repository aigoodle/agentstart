package io.aigoodle.observability.service;

import io.aigoodle.observability.api.LlmCallMeasurement;
import io.aigoodle.observability.api.TokenUsage;
import io.aigoodle.observability.entity.LlmCallRecord;
import java.util.function.ToLongBiFunction;

/** Converts an invocation measurement into its persistence representation. */
final class LlmCallRecordFactory {

    private final ToLongBiFunction<String, TokenUsage> costCalculator;

    LlmCallRecordFactory(ToLongBiFunction<String, TokenUsage> costCalculator) {
        this.costCalculator = costCalculator;
    }

    LlmCallRecord create(LlmCallMeasurement measurement) {
        TokenUsage tokenUsage = measurement.tokenUsage();
        LlmCallRecord record = new LlmCallRecord();
        record.setTenantId(measurement.tenantId());
        record.setAppId(measurement.appId());
        record.setProvider(measurement.provider());
        record.setModel(measurement.model());
        record.setPromptTokens(tokenUsage.promptTokens());
        record.setCompletionTokens(tokenUsage.completionTokens());
        record.setTotalTokens(tokenUsage.totalTokens());
        record.setCostMicros(costCalculator.applyAsLong(measurement.model(), tokenUsage));
        record.setLatencyMs(measurement.latencyMs());
        record.setSuccess(measurement.successful());
        record.setErrorType(measurement.errorType());
        return record;
    }
}

package io.aigoodle.observability.api;

/**
 * The measured outcome of one model invocation.
 *
 * <p>This value object keeps provider, model, usage and outcome together so callers
 * cannot accidentally swap adjacent primitive parameters when recording metrics.</p>
 */
public record LlmCallMeasurement(
        String provider,
        String model,
        String tenantId,
        TokenUsage tokenUsage,
        long latencyMs,
        boolean successful,
        String errorType,
        String appId) {

    public LlmCallMeasurement(
            String provider,
            String model,
            String tenantId,
            TokenUsage tokenUsage,
            long latencyMs,
            boolean successful,
            String errorType) {
        this(provider, model, tenantId, tokenUsage, latencyMs, successful, errorType, null);
    }

    public LlmCallMeasurement {
        tokenUsage = tokenUsage == null ? TokenUsage.ZERO : tokenUsage;
    }

    public static LlmCallMeasurement successful(
            String provider, String model, String tenantId, TokenUsage tokenUsage, long latencyMs) {
        return successful(new ModelCallContext(provider, model, tenantId), tokenUsage, latencyMs);
    }

    public static LlmCallMeasurement successful(ModelCallContext callContext, TokenUsage tokenUsage, long latencyMs) {
        return new LlmCallMeasurement(
                callContext.provider(),
                callContext.model(),
                callContext.tenantId(),
                tokenUsage,
                latencyMs,
                true,
                null,
                callContext.appId());
    }

    public static LlmCallMeasurement failed(
            String provider, String model, String tenantId, long latencyMs, String errorType) {
        return failed(new ModelCallContext(provider, model, tenantId), latencyMs, errorType);
    }

    public static LlmCallMeasurement failed(ModelCallContext callContext, long latencyMs, String errorType) {
        return new LlmCallMeasurement(
                callContext.provider(),
                callContext.model(),
                callContext.tenantId(),
                TokenUsage.ZERO,
                latencyMs,
                false,
                errorType,
                callContext.appId());
    }
}

package io.aigoodle.observability.metering;

import io.aigoodle.common.context.ModelExecutionScope;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.observability.api.LlmCallMeasurement;
import io.aigoodle.observability.api.ModelCallContext;
import io.aigoodle.observability.api.TokenUsage;
import io.aigoodle.observability.service.LlmMetricsService;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * A {@link ChatModel} decorator that times each call and records token usage / cost /
 * success into {@link LlmMetricsService}. Wraps the non-streaming {@code call(Prompt)}
 * path (which Spring AI's tool-calling loop uses), so every model round is captured.
 */
public class MeteringChatModel implements ChatModel {

    private final ChatModel delegate;
    private final ModelCallContext callContext;
    private final LlmMetricsService metricsService;

    public MeteringChatModel(ChatModel delegate, String provider, String model, LlmMetricsService metricsService) {
        this(delegate, ModelCallContext.of(provider, model), metricsService);
    }

    public MeteringChatModel(ChatModel delegate, ModelCallContext callContext, LlmMetricsService metricsService) {
        this.delegate = delegate;
        this.callContext = callContext;
        this.metricsService = metricsService;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        long startedAtNanos = System.nanoTime();
        ModelCallContext invocation = invocationContext();
        try {
            ChatResponse response = delegate.call(prompt);
            recordSuccess(invocation, extractUsage(response), startedAtNanos);
            return response;
        } catch (RuntimeException exception) {
            recordFailure(invocation, exception, startedAtNanos);
            if (exception instanceof PlatformException) {
                throw exception;
            }
            String exceptionMessage = exception.getMessage();
            String detail = exceptionMessage == null || exceptionMessage.isBlank()
                    ? exception.getClass().getSimpleName()
                    : exceptionMessage.strip();
            throw new PlatformException(
                    "model_call_failed",
                    "Model call failed (provider=" + callContext.provider() + ", model=" + callContext.model() + "): "
                            + detail,
                    exception);
        }
    }

    /**
     * Streaming path — needed by the agent chat SSE endpoint. Without an
     * override, Spring AI's default {@code stream()} throws
     * {@code UnsupportedOperationException("streaming is not supported")}
     * which broke typewriter output for wrapped models. We delegate + hook the
     * Flux lifecycle for metrics, aggregating token usage from the final chunk
     * (Spring AI populates {@code ChatResponse.metadata.usage} only on the
     * terminal frame during streaming).
     */
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        ModelCallContext invocation = invocationContext();
        return Flux.defer(() -> meterStreamSubscription(prompt, invocation));
    }

    private Flux<ChatResponse> meterStreamSubscription(Prompt prompt, ModelCallContext invocation) {
        StreamMeasurement measurement = new StreamMeasurement(invocation);
        Flux<ChatResponse> responseStream;
        try {
            responseStream = delegate.stream(prompt);
        } catch (RuntimeException streamFailure) {
            measurement.recordFailure(streamFailure);
            return Flux.error(streamFailure);
        }
        return responseStream
                .doOnNext(measurement::observe)
                .doOnComplete(measurement::recordSuccess)
                .doOnError(measurement::recordFailure)
                .doOnCancel(measurement::recordCancellation);
    }

    @Override
    public ChatOptions getOptions() {
        return delegate.getOptions();
    }

    private void recordSuccess(ModelCallContext invocation, TokenUsage tokenUsage, long startedAtNanos) {
        metricsService.record(
                LlmCallMeasurement.successful(invocation, tokenUsage, elapsedMilliseconds(startedAtNanos)));
    }

    private void recordFailure(ModelCallContext invocation, Throwable exception, long startedAtNanos) {
        metricsService.record(LlmCallMeasurement.failed(
                invocation,
                elapsedMilliseconds(startedAtNanos),
                exception.getClass().getSimpleName()));
    }

    private ModelCallContext invocationContext() {
        ModelExecutionScope.Attribution attribution = ModelExecutionScope.current();
        return new ModelCallContext(
                callContext.provider(),
                callContext.model(),
                attribution == null
                        ? (callContext.tenantId() == null
                                ? UserContextHolder.currentTenantId()
                                : callContext.tenantId())
                        : attribution.tenantId(),
                attribution == null
                        ? (callContext.appId() == null ? UserContextHolder.currentAppId() : callContext.appId())
                        : attribution.appId());
    }

    private static long elapsedMilliseconds(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private static TokenUsage extractUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return TokenUsage.ZERO;
        }
        ChatResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata.getUsage();
        if (usage == null) {
            return TokenUsage.ZERO;
        }
        return TokenUsage.of(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    /** Holds the mutable metering state belonging to exactly one stream subscription. */
    private final class StreamMeasurement {
        private final ModelCallContext invocation;

        private StreamMeasurement(ModelCallContext invocation) {
            this.invocation = invocation;
        }

        private final long startedAtNanos = System.nanoTime();
        private final AtomicReference<TokenUsage> latestUsage = new AtomicReference<>(TokenUsage.ZERO);
        private final AtomicBoolean recorded = new AtomicBoolean();

        void observe(ChatResponse responseChunk) {
            TokenUsage tokenUsage = extractUsage(responseChunk);
            if (tokenUsage.totalTokens() > 0) {
                latestUsage.set(tokenUsage);
            }
        }

        void recordSuccess() {
            recordOnce(() -> MeteringChatModel.this.recordSuccess(invocation, latestUsage.get(), startedAtNanos));
        }

        void recordFailure(Throwable streamFailure) {
            recordOnce(() -> MeteringChatModel.this.recordFailure(invocation, streamFailure, startedAtNanos));
        }

        void recordCancellation() {
            recordFailure(new CancellationException("stream cancelled"));
        }

        private void recordOnce(Runnable recorder) {
            if (recorded.compareAndSet(false, true)) {
                recorder.run();
            }
        }
    }
}

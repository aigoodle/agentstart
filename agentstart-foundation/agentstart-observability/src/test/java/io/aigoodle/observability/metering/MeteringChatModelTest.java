package io.aigoodle.observability.metering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.observability.api.LlmCallMeasurement;
import io.aigoodle.observability.service.LlmMetricsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

class MeteringChatModelTest {

    @Test
    void streamKeepsAppAttributionAfterScopeClosesAndThreadChanges() {
        ChatModel delegate = mock(ChatModel.class);
        LlmMetricsService metrics = mock(LlmMetricsService.class);
        when(delegate.stream(any(Prompt.class))).thenReturn(Flux.empty());
        var model = new MeteringChatModel(delegate, "p", "m", metrics);
        Flux<?> stream;
        try (var scope = io.aigoodle.common.context.ModelExecutionScope.open("tenant-stream", "app-stream")) {
            stream = model.stream(mock(Prompt.class));
        }
        stream.subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()).blockLast();
        var capture = ArgumentCaptor.forClass(LlmCallMeasurement.class);
        verify(metrics).record(capture.capture());
        assertThat(capture.getValue().appId()).isEqualTo("app-stream");
        assertThat(capture.getValue().tenantId()).isEqualTo("tenant-stream");
    }

    @Test
    void preservesProviderSpecificOptionsRequiredBySpringAi2ChatClient() {
        ChatModel delegate = mock(ChatModel.class);
        LlmMetricsService metricsService = mock(LlmMetricsService.class);
        OpenAiChatOptions options =
                OpenAiChatOptions.builder().model("qwen-plus").build();
        when(delegate.getOptions()).thenReturn(options);

        MeteringChatModel meteredModel = new MeteringChatModel(delegate, "qwen", "qwen-plus", metricsService);

        ChatOptions exposedOptions = meteredModel.getOptions();
        assertThat(exposedOptions).isSameAs(options);
        assertThat(exposedOptions.mutate().build()).isInstanceOf(OpenAiChatOptions.class);
    }

    @Test
    void recordsEachStreamSubscriptionIndependently() {
        ChatModel delegate = mock(ChatModel.class);
        LlmMetricsService metricsService = mock(LlmMetricsService.class);
        Prompt prompt = mock(Prompt.class);
        when(delegate.stream(prompt)).thenReturn(Flux.empty());
        MeteringChatModel meteredModel = new MeteringChatModel(delegate, "openai", "gpt-4o", metricsService);

        Flux<?> responseStream = meteredModel.stream(prompt);
        responseStream.blockLast();
        responseStream.blockLast();

        verify(delegate, times(2)).stream(prompt);
        verify(metricsService, times(2)).record(any(LlmCallMeasurement.class));
    }

    @Test
    void recordsCancelledStreamAsFailure() {
        ChatModel delegate = mock(ChatModel.class);
        LlmMetricsService metricsService = mock(LlmMetricsService.class);
        Prompt prompt = mock(Prompt.class);
        when(delegate.stream(prompt)).thenReturn(Flux.never());
        MeteringChatModel meteredModel = new MeteringChatModel(delegate, "openai", "gpt-4o", metricsService);

        Disposable subscription = meteredModel.stream(prompt).subscribe();
        subscription.dispose();

        ArgumentCaptor<LlmCallMeasurement> measurementCaptor = ArgumentCaptor.forClass(LlmCallMeasurement.class);
        verify(metricsService).record(measurementCaptor.capture());
        assertThat(measurementCaptor.getValue().successful()).isFalse();
        assertThat(measurementCaptor.getValue().errorType()).isEqualTo("CancellationException");
    }
}

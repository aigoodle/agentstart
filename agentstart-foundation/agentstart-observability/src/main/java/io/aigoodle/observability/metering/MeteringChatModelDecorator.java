package io.aigoodle.observability.metering;

import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.runtime.ChatModelDecorator;
import io.aigoodle.observability.api.ModelCallContext;
import io.aigoodle.observability.service.LlmMetricsService;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Plugs metering into the model module: every chat model the factory builds is wrapped
 * in a {@link MeteringChatModel}.
 */
public class MeteringChatModelDecorator implements ChatModelDecorator {

    private final LlmMetricsService metricsService;

    public MeteringChatModelDecorator(LlmMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    public ChatModel decorate(ChatModel delegate, ModelEndpoint endpoint) {
        ModelCallContext callContext = ModelCallContext.of(endpoint.getProviderName(), endpoint.getModelName());
        return new MeteringChatModel(delegate, callContext, metricsService);
    }
}

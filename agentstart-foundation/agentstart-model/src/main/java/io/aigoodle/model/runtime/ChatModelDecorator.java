package io.aigoodle.model.runtime;

import io.aigoodle.model.provider.ModelEndpoint;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Extension point for wrapping every chat model the factory builds — e.g. to add
 * metering/observability, caching or guardrails — without the model module depending
 * on those concerns. Decorators are applied in bean order; publish one as a Spring bean.
 */
public interface ChatModelDecorator {

    ChatModel decorate(ChatModel delegate, ModelEndpoint endpoint);
}

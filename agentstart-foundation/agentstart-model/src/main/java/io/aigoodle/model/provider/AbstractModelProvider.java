package io.aigoodle.model.provider;

import io.aigoodle.model.enums.ModelType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Convenience base that makes every model type "unsupported" by default, so a
 * concrete provider only overrides the types it actually serves.
 */
public abstract class AbstractModelProvider implements ModelProvider {

    @Override
    public ChatModel createChatModel(ModelEndpoint endpoint) {
        throw new UnsupportedOperationException(getName() + " does not support model type " + ModelType.LLM);
    }

    @Override
    public EmbeddingModel createEmbeddingModel(ModelEndpoint endpoint) {
        throw new UnsupportedOperationException(getName() + " does not support model type " + ModelType.TEXT_EMBEDDING);
    }

    protected void requireApiKey(ModelEndpoint endpoint) {
        if (endpoint.getApiKey() == null || endpoint.getApiKey().isBlank()) {
            throw new IllegalArgumentException("Provider '" + getName() + "' requires an api key");
        }
    }
}

package io.aigoodle.model.service;

import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.registry.ModelProviderRegistry;
import io.aigoodle.model.runtime.ModelInstanceFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/** Executes the bounded, user-facing connectivity check for a configured model. */
public class ModelConnectionTester {

    private static final Logger logger = LoggerFactory.getLogger(ModelConnectionTester.class);
    private static final int MAX_CHAT_SNIPPET_LENGTH = 200;

    private final ModelProviderRegistry providerRegistry;
    private final ModelInstanceFactory instanceFactory;

    public ModelConnectionTester(ModelProviderRegistry providerRegistry, ModelInstanceFactory instanceFactory) {
        this.providerRegistry = providerRegistry;
        this.instanceFactory = instanceFactory;
    }

    public Map<String, Object> test(ModelEntity model, ModelEndpoint endpoint) {
        long startedAtNanos = System.nanoTime();
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            addSuccessfulProbe(result, endpoint);
            result.put("ok", true);
        } catch (RuntimeException connectionFailure) {
            logger.warn("Model {} connection test failed: {}", model.getId(), connectionFailure.getMessage());
            result.put("ok", false);
            result.put("error", connectionFailure.getMessage());
        }
        result.put("latencyMs", elapsedMilliseconds(startedAtNanos));
        return result;
    }

    private void addSuccessfulProbe(Map<String, Object> result, ModelEndpoint endpoint) {
        if (endpoint.getModelType() == ModelType.VIDEO) {
            providerRegistry
                    .get(endpoint.getProviderName())
                    .createVideoModel(endpoint)
                    .parameterSchema();
            result.put("kind", "video_configuration");
            result.put("verifiedRemote", false);
            result.put(
                    "message",
                    "Configuration validated locally. Run an explicit video generation to verify vendor access; no paid task was submitted.");
            return;
        }
        if (endpoint.getModelType() == ModelType.LLM) {
            result.put("kind", "chat");
            result.put("snippet", chatSnippet(endpoint));
            return;
        }
        if (endpoint.getModelType() == ModelType.TEXT_EMBEDDING) {
            result.put("kind", "embedding");
            result.put("dimensions", embeddingDimensions(endpoint));
            return;
        }
        result.put("kind", "unsupported");
        result.put("message", "Test not supported for " + endpoint.getModelType());
    }

    private String chatSnippet(ModelEndpoint endpoint) {
        String reply = ChatClient.builder(fastFailChatModel(endpoint))
                .build()
                .prompt()
                .user("ping")
                .call()
                .content();
        if (reply == null) {
            return "";
        }
        return reply.substring(0, Math.min(MAX_CHAT_SNIPPET_LENGTH, reply.length()));
    }

    private int embeddingDimensions(ModelEndpoint endpoint) {
        EmbeddingModel embeddingModel = instanceFactory.getOrCreate(endpoint).getEmbeddingModel();
        float[] embeddingVector = embeddingModel.embed("connection test");
        return embeddingVector == null ? 0 : embeddingVector.length;
    }

    private ChatModel fastFailChatModel(ModelEndpoint endpoint) {
        ChatModel chatModel = providerRegistry.get(endpoint.getProviderName()).createChatModel(endpoint);
        return chatModel;
    }

    private static long elapsedMilliseconds(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}

package io.aigoodle.model.runtime;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.ModelEndpoint;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * A ready-to-use, cached runtime handle for one configured model. Wraps the
 * concrete Spring AI objects so callers depend on a stable type regardless of which
 * provider built them.
 * <p>
 * Only the objects relevant to the model's {@link ModelType} are populated; asking
 * for the wrong kind throws a clear error.
 */
public class ModelInstance {

    private final String id;
    private final ModelEndpoint endpoint;
    private final ChatModel chatModel;
    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;

    private ModelInstance(
            String id,
            ModelEndpoint endpoint,
            ChatModel chatModel,
            ChatClient chatClient,
            EmbeddingModel embeddingModel) {
        this.id = id;
        this.endpoint = endpoint;
        this.chatModel = chatModel;
        this.chatClient = chatClient;
        this.embeddingModel = embeddingModel;
    }

    public static ModelInstance forChat(String id, ModelEndpoint endpoint, ChatModel chatModel) {
        return new ModelInstance(id, endpoint, chatModel, ChatClient.create(chatModel), null);
    }

    public static ModelInstance forEmbedding(String id, ModelEndpoint endpoint, EmbeddingModel embeddingModel) {
        return new ModelInstance(id, endpoint, null, null, embeddingModel);
    }

    public String getId() {
        return id;
    }

    public ModelEndpoint getEndpoint() {
        return endpoint;
    }

    public ModelType getModelType() {
        return endpoint.getModelType();
    }

    public ChatModel getChatModel() {
        if (chatModel == null) {
            throw new PlatformException(
                    "not_a_chat_model",
                    "Model '" + id + "' (" + endpoint.getModelType() + ") is not a chat model",
                    null);
        }
        return chatModel;
    }

    /** A fresh {@link ChatClient} bound to this model; callers add prompts/options. */
    public ChatClient getChatClient() {
        if (chatClient == null) {
            throw new PlatformException(
                    "not_a_chat_model",
                    "Model '" + id + "' (" + endpoint.getModelType() + ") is not a chat model",
                    null);
        }
        return chatClient;
    }

    public EmbeddingModel getEmbeddingModel() {
        if (embeddingModel == null) {
            throw new PlatformException(
                    "not_an_embedding_model",
                    "Model '" + id + "' (" + endpoint.getModelType() + ") is not an embedding model",
                    null);
        }
        return embeddingModel;
    }
}

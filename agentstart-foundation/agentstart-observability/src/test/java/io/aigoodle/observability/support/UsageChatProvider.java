package io.aigoodle.observability.support;

import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.AbstractModelProvider;
import io.aigoodle.model.provider.CredentialSchema;
import io.aigoodle.model.provider.ModelEndpoint;
import java.util.List;
import java.util.Set;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * Test provider whose chat model returns a fixed completion WITH token usage
 * (prompt=12, completion=8), so metering can be verified deterministically offline.
 */
public class UsageChatProvider extends AbstractModelProvider {

    @Override
    public String getName() {
        return "scripted";
    }

    @Override
    public Set<ModelType> supportedModelTypes() {
        return Set.of(ModelType.LLM);
    }

    @Override
    public CredentialSchema credentialSchema() {
        return CredentialSchema.of();
    }

    @Override
    public ChatModel createChatModel(ModelEndpoint endpoint) {
        return prompt -> {
            Generation gen = new Generation(new AssistantMessage("ok"));
            ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                    .usage(new DefaultUsage(12, 8, 20))
                    .build();
            return new ChatResponse(List.of(gen), metadata);
        };
    }
}

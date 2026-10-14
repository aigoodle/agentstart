package io.aigoodle.model.provider.deepseek;

import io.aigoodle.model.enums.ModelFeature;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.AbstractModelProvider;
import io.aigoodle.model.provider.CredentialField;
import io.aigoodle.model.provider.CredentialSchema;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.provider.PredefinedModel;
import java.util.List;
import java.util.Set;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;

/**
 * Native DeepSeek provider (chat only). Registered under name {@code "deepseek"}
 * so it wins over the built-in OpenAI-compatible preset.
 */
public class DeepSeekModelProvider extends AbstractModelProvider {

    public static final String NAME = "deepseek";
    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    private static final Set<ModelFeature> CHAT_FEATURES =
            Set.of(ModelFeature.STREAM, ModelFeature.TOOL_CALL, ModelFeature.STREAM_TOOL_CALL);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getLabel() {
        return "DeepSeek";
    }

    @Override
    public Set<ModelType> supportedModelTypes() {
        return Set.of(ModelType.LLM);
    }

    @Override
    public CredentialSchema credentialSchema() {
        return CredentialSchema.of(
                CredentialField.secret("apiKey", "API Key", true),
                CredentialField.builder()
                        .name("baseUrl")
                        .label("Base URL")
                        .type(CredentialField.Type.TEXT)
                        .required(false)
                        .defaultValue(DEFAULT_BASE_URL)
                        .placeholder(DEFAULT_BASE_URL)
                        .build());
    }

    @Override
    public List<PredefinedModel> predefinedModels() {
        return List.of(llm("deepseek-chat", 64_000), llm("deepseek-reasoner", 64_000));
    }

    @Override
    public ChatModel createChatModel(ModelEndpoint endpoint) {
        requireApiKey(endpoint);
        DeepSeekApi api = buildApi(endpoint);
        DeepSeekChatOptions.Builder options = DeepSeekChatOptions.builder().model(endpoint.getModelName());
        Double temperature = endpoint.decimalProperty("temperature");
        if (temperature != null) {
            options.temperature(temperature);
        }
        Double topP = endpoint.decimalProperty("topP");
        if (topP != null) {
            options.topP(topP);
        }
        Integer maxTokens = endpoint.intProperty("maxTokens");
        if (maxTokens != null) {
            options.maxTokens(maxTokens);
        }
        return DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .options(options.build())
                .build();
    }

    private static DeepSeekApi buildApi(ModelEndpoint endpoint) {
        String baseUrl = endpoint.resolveBaseUrl(DEFAULT_BASE_URL);
        DeepSeekApi.Builder builder = DeepSeekApi.builder().apiKey(endpoint.getApiKey());
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    private static PredefinedModel llm(String model, int ctx) {
        return PredefinedModel.builder()
                .model(model)
                .label(model)
                .modelType(ModelType.LLM)
                .features(CHAT_FEATURES)
                .contextLength(ctx)
                .build();
    }
}

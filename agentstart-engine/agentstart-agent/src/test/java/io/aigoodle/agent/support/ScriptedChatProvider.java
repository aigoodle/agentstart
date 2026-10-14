package io.aigoodle.agent.support;

import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.AbstractModelProvider;
import io.aigoodle.model.provider.CredentialSchema;
import io.aigoodle.model.provider.ModelEndpoint;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Test provider ("scripted") that yields a {@link ScriptedChatModel} by model name.
 * Tests register a responder per model name before registering the model.
 */
public class ScriptedChatProvider extends AbstractModelProvider {

    private static final Map<String, Function<String, String>> SCRIPTS = new ConcurrentHashMap<>();

    public static void script(String modelName, Function<String, String> responder) {
        SCRIPTS.put(modelName, responder);
    }

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
        Function<String, String> responder =
                SCRIPTS.getOrDefault(endpoint.getModelName(), input -> "Final Answer: (no script)");
        return new ScriptedChatModel(responder);
    }
}

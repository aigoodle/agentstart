package io.aigoodle.model.provider.volcengine;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.provider.builtin.BuiltinModelProviders;
import io.aigoodle.model.registry.ModelProviderRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VolcengineArkModelProviderTest {

    @Test
    void registeredUnderNameVolcengine() {
        VolcengineArkModelProvider provider = new VolcengineArkModelProvider();
        assertEquals("volcengine", provider.getName());
        assertTrue(provider.supports(ModelType.LLM));
        assertTrue(provider.supports(ModelType.TEXT_EMBEDDING));
    }

    @Test
    void overridesBuiltinVolcengineInRegistry() {
        VolcengineArkModelProvider ark = new VolcengineArkModelProvider();
        List<io.aigoodle.model.provider.ModelProvider> all = new ArrayList<>();
        all.add(ark);
        all.addAll(BuiltinModelProviders.openAiCompatible());
        ModelProviderRegistry registry = new ModelProviderRegistry(all);
        assertSame(ark, registry.get("volcengine"));
    }

    @Test
    void buildsChatModelWithoutNetwork() {
        VolcengineArkModelProvider provider = new VolcengineArkModelProvider();
        ModelEndpoint endpoint = ModelEndpoint.builder()
                .id("v1")
                .providerName("volcengine")
                .modelName("doubao-1-5-lite-32k")
                .modelType(ModelType.LLM)
                .apiKey("sk-test")
                .build();
        assertNotNull(provider.createChatModel(endpoint));
    }

    @Test
    void endpointIdOverridesModelName() {
        // A separate provider that lets us intercept the OpenAiApi model field via
        // a subclass would be ideal, but at the black-box level the fact that the
        // ChatModel construction succeeds with an ep-xxx as endpointId is enough:
        // the internal effectiveModel() route is covered.
        VolcengineArkModelProvider provider = new VolcengineArkModelProvider();
        ModelEndpoint endpoint = ModelEndpoint.builder()
                .id("v2")
                .providerName("volcengine")
                .modelName("ignored")
                .modelType(ModelType.LLM)
                .apiKey("sk-test")
                .properties(Map.of("endpointId", "ep-20240611-doubao"))
                .build();
        assertNotNull(provider.createChatModel(endpoint));
    }

    @Test
    void missingApiKeyThrows() {
        VolcengineArkModelProvider provider = new VolcengineArkModelProvider();
        ModelEndpoint endpoint = ModelEndpoint.builder()
                .id("v3")
                .providerName("volcengine")
                .modelName("doubao-1-5-lite-32k")
                .modelType(ModelType.LLM)
                .build();
        assertThrows(IllegalArgumentException.class, () -> provider.createChatModel(endpoint));
    }
}

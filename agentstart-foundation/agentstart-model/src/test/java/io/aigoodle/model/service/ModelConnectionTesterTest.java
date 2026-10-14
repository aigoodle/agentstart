package io.aigoodle.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.provider.ModelProvider;
import io.aigoodle.model.registry.ModelProviderRegistry;
import io.aigoodle.model.runtime.ModelInstanceFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelConnectionTesterTest {

    @Test
    void describesUnsupportedModelTypesWithoutCallingAProvider() {
        ModelConnectionTester tester =
                new ModelConnectionTester(mock(ModelProviderRegistry.class), mock(ModelInstanceFactory.class));

        Map<String, Object> result = tester.test(model("model-1"), endpoint(ModelType.RERANK));

        assertThat(result)
                .containsEntry("ok", true)
                .containsEntry("kind", "unsupported")
                .containsEntry("message", "Test not supported for RERANK")
                .containsKey("latencyMs");
    }

    @Test
    void turnsProviderFailuresIntoAUserFacingTestResult() {
        ModelProviderRegistry providerRegistry = mock(ModelProviderRegistry.class);
        ModelProvider provider = mock(ModelProvider.class);
        ModelEndpoint endpoint = endpoint(ModelType.LLM);
        when(providerRegistry.get("openai")).thenReturn(provider);
        when(provider.createChatModel(endpoint)).thenThrow(new IllegalStateException("invalid API key"));
        ModelConnectionTester tester = new ModelConnectionTester(providerRegistry, mock(ModelInstanceFactory.class));

        Map<String, Object> result = tester.test(model("model-1"), endpoint);

        assertThat(result)
                .containsEntry("ok", false)
                .containsEntry("error", "invalid API key")
                .containsKey("latencyMs");
    }

    private static ModelEntity model(String id) {
        ModelEntity model = new ModelEntity();
        model.setId(id);
        return model;
    }

    private static ModelEndpoint endpoint(ModelType modelType) {
        return ModelEndpoint.builder()
                .id("model-1")
                .providerName("openai")
                .modelName("test-model")
                .modelType(modelType)
                .build();
    }
}

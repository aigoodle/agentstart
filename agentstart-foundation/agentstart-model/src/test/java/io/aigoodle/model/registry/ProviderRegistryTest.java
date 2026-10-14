package io.aigoodle.model.registry;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.provider.builtin.BuiltinModelProviders;
import io.aigoodle.model.provider.builtin.OllamaModelProvider;
import io.aigoodle.model.runtime.ModelInstance;
import io.aigoodle.model.runtime.ModelInstanceFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Verifies provider discovery and that the factory can build real Spring AI model
 * objects (object construction only — no network call).
 */
class ProviderRegistryTest {

    private ModelProviderRegistry registry() {
        List<io.aigoodle.model.provider.ModelProvider> all = new ArrayList<>();
        all.add(new OllamaModelProvider());
        all.addAll(BuiltinModelProviders.openAiCompatible());
        return new ModelProviderRegistry(all);
    }

    @Test
    void builtinProvidersAreRegistered() {
        ModelProviderRegistry registry = registry();
        assertTrue(registry.contains("openai"));
        assertTrue(registry.contains("deepseek"));
        assertTrue(registry.contains("zhipu"));
        assertTrue(registry.contains("ollama"));
        assertTrue(registry.get("openai").supports(ModelType.LLM));
        assertTrue(registry.get("openai").supports(ModelType.TEXT_EMBEDDING));
    }

    @Test
    void unknownProviderThrows() {
        assertThrows(RuntimeException.class, () -> registry().get("does-not-exist"));
    }

    @Test
    void factoryBuildsOpenAiChatModel() {
        ModelInstanceFactory factory = new ModelInstanceFactory(registry());
        ModelEndpoint endpoint = ModelEndpoint.builder()
                .id("m1")
                .providerName("openai")
                .modelName("gpt-4o-mini")
                .modelType(ModelType.LLM)
                .apiKey("sk-test")
                .build();
        ModelInstance instance = factory.getOrCreate(endpoint);
        assertNotNull(instance.getChatClient());
        assertInstanceOf(ChatModel.class, instance.getChatModel());
        // cached
        assertSame(instance, factory.getOrCreate(endpoint));
    }

    @Test
    void factoryBuildsOpenAiEmbeddingModel() {
        ModelInstanceFactory factory = new ModelInstanceFactory(registry());
        ModelEndpoint endpoint = ModelEndpoint.builder()
                .id("e1")
                .providerName("openai")
                .modelName("text-embedding-3-small")
                .modelType(ModelType.TEXT_EMBEDDING)
                .apiKey("sk-test")
                .build();
        ModelInstance instance = factory.getOrCreate(endpoint);
        assertInstanceOf(EmbeddingModel.class, instance.getEmbeddingModel());
    }

    @Test
    void modelInstanceCacheIsIsolatedByTenantEvenWhenEndpointIdsMatch() {
        ModelInstanceFactory factory = new ModelInstanceFactory(registry());
        ModelEndpoint tenantA = ModelEndpoint.builder()
                .tenantId("tenant-a")
                .id("local-model-1")
                .providerName("openai")
                .modelName("gpt-4o-mini")
                .modelType(ModelType.LLM)
                .apiKey("sk-tenant-a")
                .build();
        ModelEndpoint tenantB = ModelEndpoint.builder()
                .tenantId("tenant-b")
                .id("local-model-1")
                .providerName("openai")
                .modelName("gpt-4o-mini")
                .modelType(ModelType.LLM)
                .apiKey("sk-tenant-b")
                .build();

        assertNotSame(factory.getOrCreate(tenantA), factory.getOrCreate(tenantB));
        assertSame(factory.getOrCreate(tenantA), factory.getOrCreate(tenantA));
    }

    @Test
    void tenantScopedEvictionDoesNotInvalidateAnotherTenantsMatchingEndpointId() {
        ModelInstanceFactory factory = new ModelInstanceFactory(registry());
        ModelEndpoint tenantA = ModelEndpoint.builder()
                .tenantId("tenant-a")
                .id("shared-local-id")
                .providerName("openai")
                .modelName("gpt-4o-mini")
                .modelType(ModelType.LLM)
                .apiKey("sk-tenant-a")
                .build();
        ModelEndpoint tenantB = ModelEndpoint.builder()
                .tenantId("tenant-b")
                .id("shared-local-id")
                .providerName("openai")
                .modelName("gpt-4o-mini")
                .modelType(ModelType.LLM)
                .apiKey("sk-tenant-b")
                .build();
        ModelInstance beforeA = factory.getOrCreate(tenantA);
        ModelInstance beforeB = factory.getOrCreate(tenantB);

        factory.evict("tenant-a", "shared-local-id");

        assertNotSame(beforeA, factory.getOrCreate(tenantA));
        assertSame(beforeB, factory.getOrCreate(tenantB));
    }

    @Test
    void deepseekUsesItsDefaultBaseUrlWithoutError() {
        ModelInstanceFactory factory = new ModelInstanceFactory(registry());
        ModelEndpoint endpoint = ModelEndpoint.builder()
                .id("d1")
                .providerName("deepseek")
                .modelName("deepseek-chat")
                .modelType(ModelType.LLM)
                .apiKey("sk-test")
                .build();
        assertNotNull(factory.getOrCreate(endpoint).getChatModel());
    }

    @Test
    void missingApiKeyForOpenAiThrows() {
        ModelInstanceFactory factory = new ModelInstanceFactory(registry());
        ModelEndpoint endpoint = ModelEndpoint.builder()
                .id("x1")
                .providerName("openai")
                .modelName("gpt-4o")
                .modelType(ModelType.LLM)
                .build();
        assertThrows(IllegalArgumentException.class, () -> factory.build(endpoint));
    }

    @Test
    void appBeansOverrideBuiltinsWithSameName() {
        io.aigoodle.model.provider.ModelProvider fakeZhipu = new io.aigoodle.model.provider.AbstractModelProvider() {
            @Override
            public String getName() {
                return "zhipu";
            }

            @Override
            public java.util.Set<ModelType> supportedModelTypes() {
                return java.util.Set.of(ModelType.LLM);
            }

            @Override
            public io.aigoodle.model.provider.CredentialSchema credentialSchema() {
                return io.aigoodle.model.provider.CredentialSchema.of();
            }
        };
        List<io.aigoodle.model.provider.ModelProvider> all = new ArrayList<>();
        all.add(fakeZhipu); // app bean first
        all.addAll(BuiltinModelProviders.openAiCompatible()); // built-ins after
        ModelProviderRegistry registry = new ModelProviderRegistry(all);
        assertSame(
                fakeZhipu, registry.get("zhipu"), "app-published provider must beat the built-in with the same name");
    }
}

package io.aigoodle.model.service;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.model.ModelTestApplication;
import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.entity.ProviderCredentialEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.ModelEndpoint;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full persistence round-trip on H2: register a model, read it back, resolve its
 * (decrypted, merged) endpoint and obtain a cached runtime instance.
 */
@SpringBootTest(classes = ModelTestApplication.class)
class ModelServiceIntegrationTest {

    @Autowired
    private ModelService modelService;

    @Autowired
    private ProviderCredentialService credentialService;

    @Autowired
    private ProviderModelSettingsService modelSettingsService;

    @Test
    void registerResolveAndInstantiate() {
        ModelEntity entity = modelService.register(ModelRegistration.builder()
                .tenantId("acme")
                .providerName("openai")
                .modelName("gpt-4o-mini")
                .modelType(ModelType.LLM)
                .credentials(Map.of("apiKey", "sk-integration", "baseUrl", "https://api.example.com"))
                .asDefault(true)
                .build());

        assertNotNull(entity.getId());

        // persisted + readable
        ModelEntity loaded = modelService.require("acme", entity.getId());
        assertEquals("openai", loaded.getProviderName());
        assertEquals(ModelType.LLM, loaded.getModelType());
        assertNotEquals("sk-integration", loaded.getEncryptedConfig(), "secret must be encrypted at rest");

        // endpoint decrypts + splits apiKey/baseUrl
        ModelEndpoint endpoint = modelService.resolveEndpoint("acme", entity.getId());
        assertEquals("sk-integration", endpoint.getApiKey());
        assertEquals("https://api.example.com", endpoint.getBaseUrl());

        // runtime instance
        assertNotNull(modelService.getModelInstance("acme", entity.getId()).getChatClient());

        // default lookup
        ModelEntity def = modelService.getDefault("acme", ModelType.LLM);
        assertEquals(entity.getId(), def.getId());

        // listing by type
        List<ModelEntity> llms = modelService.listByType("acme", ModelType.LLM);
        assertEquals(1, llms.size());
    }

    @Test
    void updateCredentialsMergesPatchIntoExisting() {
        ModelEntity entity = modelService.register(ModelRegistration.builder()
                .tenantId("rotate")
                .providerName("openai")
                .modelName("gpt-4o")
                .modelType(ModelType.LLM)
                .credentials(Map.of("apiKey", "sk-old", "baseUrl", "https://old.example.com"))
                .build());

        // Rotate ONLY the apiKey — baseUrl should survive.
        modelService.updateCredentials("rotate", entity.getId(), Map.of("apiKey", "sk-new"));

        ModelEndpoint endpoint = modelService.resolveEndpoint("rotate", entity.getId());
        assertEquals("sk-new", endpoint.getApiKey(), "apiKey should be replaced");
        assertEquals(
                "https://old.example.com", endpoint.getBaseUrl(), "baseUrl should be preserved by the merge semantics");

        // Empty patch is a no-op.
        modelService.updateCredentials("rotate", entity.getId(), Map.of());
        endpoint = modelService.resolveEndpoint("rotate", entity.getId());
        assertEquals("sk-new", endpoint.getApiKey());
        assertEquals("https://old.example.com", endpoint.getBaseUrl());
    }

    @Test
    void onlyOneDefaultPerType() {
        modelService.register(ModelRegistration.builder()
                .tenantId("dft")
                .providerName("openai")
                .modelName("gpt-4o")
                .modelType(ModelType.LLM)
                .credentials(Map.of("apiKey", "k1"))
                .asDefault(true)
                .build());
        ModelEntity second = modelService.register(ModelRegistration.builder()
                .tenantId("dft")
                .providerName("deepseek")
                .modelName("deepseek-chat")
                .modelType(ModelType.LLM)
                .credentials(Map.of("apiKey", "k2"))
                .asDefault(true)
                .build());

        ModelEntity def = modelService.getDefault("dft", ModelType.LLM);
        assertEquals(second.getId(), def.getId(), "registering a new default unsets the previous one");
    }

    @Test
    void sharedProviderCredentialIsInherited() {
        ProviderCredentialEntity credential = credentialService.save(new ProviderCredentialRegistration(
                "shared",
                "openai",
                "team-key",
                Map.of("apiKey", "sk-shared", "baseUrl", "https://shared.example.com")));

        ModelEntity model = modelService.register(ModelRegistration.builder()
                .tenantId("shared")
                .providerName("openai")
                .modelName("gpt-4o")
                .modelType(ModelType.LLM)
                .credentialId(credential.getId())
                .build());

        ModelEndpoint endpoint = modelService.resolveEndpoint("shared", model.getId());
        assertEquals("sk-shared", endpoint.getApiKey());
        assertEquals("https://shared.example.com", endpoint.getBaseUrl());
    }

    @Test
    void validateRejectsMissingApiKey() {
        assertThrows(
                RuntimeException.class,
                () -> modelService.validate(ModelRegistration.builder()
                        .providerName("openai")
                        .modelName("gpt-4o")
                        .modelType(ModelType.LLM)
                        .credentials(Map.of())
                        .build()));
    }

    @Test
    void tenantScopedManagementCannotReadOrMutateAnotherTenantsModel() {
        ModelEntity owned = modelService.register(ModelRegistration.builder()
                .tenantId("isolated-a")
                .providerName("openai")
                .modelName("gpt-4o-mini")
                .modelType(ModelType.LLM)
                .credentials(Map.of("apiKey", "tenant-a-secret"))
                .build());

        assertThrows(RuntimeException.class, () -> modelService.require("isolated-b", owned.getId()));
        assertThrows(
                RuntimeException.class,
                () -> modelService.updateCredentials("isolated-b", owned.getId(), Map.of("apiKey", "attacker-secret")));
        assertThrows(RuntimeException.class, () -> modelService.setEnabled("isolated-b", owned.getId(), false));
        assertThrows(RuntimeException.class, () -> modelService.delete("isolated-b", owned.getId()));

        ModelEntity stillOwned = modelService.require("isolated-a", owned.getId());
        assertTrue(stillOwned.getEnabled());
        assertEquals("tenant-a-secret", modelService.resolveEndpoint(stillOwned).getApiKey());
    }

    @Test
    void persistenceGuardRejectsTenantArgumentsThatDisagreeWithHostContext() {
        ModelEntity owned = modelService.register(ModelRegistration.builder()
                .tenantId("guard-a")
                .providerName("openai")
                .modelName("gpt-4o-mini")
                .modelType(ModelType.LLM)
                .credentials(Map.of("apiKey", "guard-secret"))
                .build());
        CurrentUser tenantA =
                CurrentUser.builder().tenantId("guard-a").userId("employee-a").build();

        ModelEntity visible = UserContextHolder.callAs(tenantA, () -> modelService.require("guard-a", owned.getId()));
        assertEquals(owned.getId(), visible.getId());
        assertThrows(
                RuntimeException.class,
                () -> UserContextHolder.callAs(tenantA, () -> modelService.require("guard-b", owned.getId())));
        assertThrows(
                RuntimeException.class,
                () -> UserContextHolder.callAs(
                        tenantA,
                        () -> modelService.register(ModelRegistration.builder()
                                .tenantId("guard-b")
                                .providerName("openai")
                                .modelName("gpt-4o")
                                .modelType(ModelType.LLM)
                                .credentials(Map.of("apiKey", "foreign"))
                                .build())));
    }

    @Test
    void persistenceGuardProtectsCredentialsSettingsAndTenantDefaults() {
        ProviderCredentialEntity credential = credentialService.save(new ProviderCredentialRegistration(
                "security-a", "openai", "primary", Map.of("apiKey", "security-secret")));
        CurrentUser tenantA =
                CurrentUser.builder().tenantId("security-a").userId("admin-a").build();

        Map<String, Object> decoded = UserContextHolder.callAs(
                tenantA, () -> credentialService.decodeCredentials("security-a", credential.getId()));
        assertEquals("security-secret", decoded.get("apiKey"));
        assertThrows(
                RuntimeException.class,
                () -> UserContextHolder.callAs(
                        tenantA, () -> credentialService.decodeCredentials("security-b", credential.getId())));

        UserContextHolder.runAs(tenantA, () -> {
            modelSettingsService.setEnabled("security-a", "openai", "gpt-4o-mini", ModelType.LLM, true);
            modelSettingsService.setDefault("security-a", "openai", "gpt-4o-mini", ModelType.LLM);
        });
        assertThrows(
                RuntimeException.class,
                () -> UserContextHolder.callAs(
                        tenantA,
                        () -> modelSettingsService.findSetting("security-b", "openai", "gpt-4o-mini", ModelType.LLM)));
        assertThrows(
                RuntimeException.class,
                () -> UserContextHolder.callAs(
                        tenantA, () -> modelSettingsService.getDefault("security-b", ModelType.LLM)));
    }
}

package io.aigoodle.model.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.aigoodle.model.entity.ProviderCredentialEntity;
import io.aigoodle.model.mapper.ModelMapper;
import io.aigoodle.model.provider.CredentialField;
import io.aigoodle.model.provider.CredentialSchema;
import io.aigoodle.model.provider.ModelProvider;
import io.aigoodle.model.registry.ModelProviderRegistry;
import io.aigoodle.model.runtime.ModelInstanceFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderCredentialConfigurationTest {
    private final ProviderCredentialService credentials = mock(ProviderCredentialService.class);
    private final ProviderDefinitionService definitions = mock(ProviderDefinitionService.class);
    private final ModelProvider provider = mock(ModelProvider.class);

    private ModelService service() {
        when(provider.getName()).thenReturn("custom");
        when(provider.implementationKey()).thenReturn("openai-compatible");
        when(provider.credentialSchema())
                .thenReturn(CredentialSchema.of(
                        CredentialField.secret("apiKey", "API Key", true),
                        CredentialField.builder()
                                .name("baseUrl")
                                .label("Base URL")
                                .type(CredentialField.Type.TEXT)
                                .required(true)
                                .build()));
        return new ModelService(
                mock(ModelMapper.class),
                credentials,
                mock(CredentialCodec.class),
                new ModelProviderRegistry(List.of(provider)),
                mock(ModelInstanceFactory.class),
                definitions,
                mock(ProviderModelSettingsService.class),
                mock(ModelConnectionTester.class),
                mock(ProviderCatalogClient.class));
    }

    @Test
    void incompleteNewConfigurationDoesNotWriteCatalogOrCredential() {
        assertThatThrownBy(() ->
                        service().saveProviderCredentialWithValidation("tenant-a", "custom", Map.of("apiKey", "test")))
                .hasMessageContaining("Base URL");
        verifyNoInteractions(definitions);
        verify(credentials, never()).upsertPrimary(anyString(), anyString(), anyMap());
    }

    @Test
    void partialEditUsesStoredSecretAndOnlyWritesThePatch() {
        var existing = new ProviderCredentialEntity();
        existing.setId("credential-a");
        when(credentials.findPrimary("tenant-a", "custom")).thenReturn(existing);
        when(credentials.decodeCredentials("tenant-a", "credential-a"))
                .thenReturn(Map.of("apiKey", "stored-secret", "baseUrl", "https://old.example/v1"));
        Map<String, Object> patch = Map.of("baseUrl", "https://new.example/v1");
        service().saveProviderCredentialWithValidation("tenant-a", "custom", patch);
        verify(credentials).upsertPrimary("tenant-a", "custom", patch);
    }
}
